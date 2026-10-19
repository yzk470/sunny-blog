/**
 * SceneManager — orchestrates the 3D scene, model loading, expression, and motion.
 *
 * Responsibilities:
 * - Create WebGLRenderer, Scene, PerspectiveCamera, lights
 * - Load / switch characters (yaoguang, jingliu)
 * - Animation loop (clock, engines, render)
 * - Window resize handler
 * - Cleanup / dispose
 *
 * Dependencies: ModelLoader, ExpressionEngine, MotionEngine
 */

import * as THREE from 'three';
import { retargetClip } from 'three/addons/utils/SkeletonUtils.js';
import { MMDPhysics } from 'three/addons/animation/MMDPhysics.js';
import { ModelLoader } from './ModelLoader.js';
import { ExpressionEngine } from './ExpressionEngine.js';
import { MotionEngine } from './MotionEngine.js';
import { LipSync } from './LipSync.js';

export class SceneManager {
    /**
     * @param {HTMLCanvasElement} canvas
     * @param {object} [options]
     * @param {function} [options.onProgress] — progress callback (0–100)
     * @param {function} [options.onReady]    — called when scene is ready
     * @param {function} [options.onError]    — called on load error
     */
    constructor(canvas, options = {}) {
        console.log('[SceneManager] Initializing SceneManager');

        /** @type {HTMLCanvasElement} */
        this.canvas = canvas;
        this.options = options;

        // ---- Renderer ----
        this.renderer = null;
        this.scene = null;
        this.camera = null;
        this.clock = new THREE.Clock();

        // ---- Current state ----
        /** @type {string|null} */
        this.currentCharacter = null;
        /** @type {THREE.SkinnedMesh|null} */
        this.currentMesh = null;
        /** @type {ModelLoader} */
        this.modelLoader = new ModelLoader();
        /** @type {ExpressionEngine|null} */
        this.expressionEngine = null;
        /** @type {MotionEngine|null} */
        this.motionEngine = null;
        /** @type {LipSync|null} — audio-driven mouth animation */
        this.lipSync = null;
        /** @type {MMDPhysics|null} — PMX skirt/hair/ribbon physics */
        this.physics = null;
        this._ammoPromise = null;
        this._modelBaseScale = null;
        this._modelBasePosition = null;
        this._modelBaseQuaternion = null;
        this._physicsBoneNames = new Set();
        this._physicsAccumulator = 0;

        // ---- Dynamic character paths (set from API) ----
        /** @type {Map<string, string>} characterCode → modelPath */
        this.characterPaths = new Map();

        // ---- Render loop ----
        this.animationFrameId = null;
        this.isRunning = false;

        // ---- Stats ----
        this.frameCount = 0;
        this.fpsLogTimer = 0;

        this._initRenderer();
        this._initScene();
        this._initCamera();
        this._initLights();
        this._bindEvents();
        this._startLoop();

        console.log('[SceneManager] Initialization complete — scene ready');
        if (options.onReady) {
            options.onReady();
        }
    }

    /**
     * Set dynamic character model paths from API data.
     * @param {Map<string, string>} paths — characterCode → modelPath
     */
    setCharacterPaths(paths) {
        this.characterPaths = paths;
        console.log('[SceneManager] Character paths set:',
            [...paths.entries()].map(e => `${e[0]}=${e[1]}`).join(', '));
    }

    // ═══════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════

    _initRenderer() {
        console.log('[SceneManager] Creating WebGLRenderer');

        this.renderer = new THREE.WebGLRenderer({
            canvas: this.canvas,
            antialias: true,
            alpha: true,
            powerPreference: 'high-performance',
        });

        this.renderer.setClearColor(0x000000, 0);
        this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        this.renderer.setSize(this.canvas.clientWidth, this.canvas.clientHeight, false);
        this.renderer.outputColorSpace = THREE.SRGBColorSpace;
        this.renderer.toneMapping = THREE.NoToneMapping; // keep raw anime colors, avoid filmic desaturation
        this.renderer.toneMappingExposure = 1.0;
        this.renderer.shadowMap.enabled = true;
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

        console.log(`[SceneManager] Renderer size: ${this.canvas.clientWidth}x${this.canvas.clientHeight}`);
    }

    _initScene() {
        console.log('[SceneManager] Creating scene');
        this.scene = new THREE.Scene();
        // Keep the canvas transparent so the Vue stage gradient/stars show through.
        this.scene.background = null;
        this.scene.fog = null;

        // A soft contact shadow anchors the feet without a hard directional shadow.
        const canvas = document.createElement('canvas');
        canvas.width = 256;
        canvas.height = 128;
        const ctx = canvas.getContext('2d');
        const gradient = ctx.createRadialGradient(128, 64, 6, 128, 64, 118);
        gradient.addColorStop(0, 'rgba(0, 0, 0, 0.58)');
        gradient.addColorStop(0.48, 'rgba(0, 0, 0, 0.2)');
        gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, 256, 128);
        const shadowTexture = new THREE.CanvasTexture(canvas);
        shadowTexture.colorSpace = THREE.SRGBColorSpace;
        this.contactShadow = new THREE.Mesh(
            new THREE.PlaneGeometry(1.25, 0.64),
            new THREE.MeshBasicMaterial({
                map: shadowTexture,
                transparent: true,
                opacity: 0.5,
                depthWrite: false,
            }),
        );
        this.contactShadow.rotation.x = -Math.PI / 2;
        this.contactShadow.position.y = 0.006;
        this.contactShadow.renderOrder = 1;
        this.scene.add(this.contactShadow);
    }

    _initCamera() {
        // Medium shot / portrait framing
        console.log('[SceneManager] Creating camera (medium shot)');

        this.camera = new THREE.PerspectiveCamera(
            45,                                                         // FOV
            this.canvas.clientWidth / Math.max(this.canvas.clientHeight, 1), // aspect
            0.1,                                                        // near
            50                                                          // far
        );

        // Positioned for a medium chest-up shot
        this.camera.position.set(0, 1.2, 3.5);
        this.camera.lookAt(0, 1.0, 0);

        console.log(`[SceneManager] Camera position: (${this.camera.position.x}, ${this.camera.position.y}, ${this.camera.position.z})`);
    }

    _initLights() {
        console.log('[SceneManager] Setting up lights');

        // Keep ambient low so textures keep their saturation and shadows stay readable.
        const hemi = new THREE.HemisphereLight('#dfe9ff', '#3b4a66', 0.32);
        this.scene.add(hemi);

        const key = new THREE.DirectionalLight('#fff1dd', 1.55);
        key.position.set(0.8, 3.3, 3.0);
        this.scene.add(key);

        const fill = new THREE.DirectionalLight('#b9d8ff', 0.28);
        fill.position.set(-2, 1.2, 1.8);
        this.scene.add(fill);

        // Warm/cool rim separates the model from the dark stage and gives it volume.
        const rim = new THREE.DirectionalLight('#ffcfdf', 0.75);
        rim.position.set(-1.4, 2.4, -2.8);
        this.scene.add(rim);

        const bottom = new THREE.DirectionalLight('#ffffff', 0.12);
        bottom.position.set(0, -0.5, 1.5);
        this.scene.add(bottom);

        console.log('[SceneManager] Lights: hemi=0.32, key=1.55, fill=0.28, rim=0.75, bottom=0.12');
    }

    /** Enhance MMD materials for richer, more solid colors. */
    _enhanceMaterials(mesh) {
        try {
            console.log('[SceneManager] Enhancing MMD materials...');
            mesh.traverse((child) => {
                if (!child.isMesh) return;
                const mats = Array.isArray(child.material) ? child.material : [child.material];
                mats.forEach((mat) => {
                    if (!mat) return;
                    if (!mat.isMeshToonMaterial && !mat.isMMDToonMaterial) return;

                    // MMD maps are sRGB; keep data textures (toon ramp / alpha) linear.
                    ['map', 'emissiveMap', 'sphereMap'].forEach((slot) => {
                        if (mat[slot] && mat[slot].isTexture) {
                            mat[slot].colorSpace = THREE.SRGBColorSpace;
                        }
                    });
                    ['gradientMap', 'alphaMap'].forEach((slot) => {
                        if (mat[slot] && mat[slot].isTexture) {
                            mat[slot].colorSpace = THREE.NoColorSpace;
                        }
                    });

                    if (mat.map) {
                        mat.map.anisotropy = Math.min(8, this.renderer.capabilities.getMaxAnisotropy());
                    }

                    // MMDLoader's ambient/emissive term tends to flatten the model.
                    // Reduce it so the key/rim lighting can carve out the form.
                    if (mat.emissive) {
                        mat.emissive.multiplyScalar(0.45);
                    }
                    mat.emissiveIntensity = 0.75;
                });
            });
            console.log('[SceneManager] Materials enhanced (sRGB, lower ambient, rim contrast)');
        } catch(e) {
            console.warn('[SceneManager] Material enhancement failed:', e.message);
        }
    }
    _bindEvents() {
        this._resizeHandler = this._onResize.bind(this);
        window.addEventListener('resize', this._resizeHandler);
        console.log('[SceneManager] Window resize listener bound');
    }

    async _loadAmmo() {
        if (window.Ammo && typeof window.Ammo !== 'function') {
            return window.Ammo;
        }
        if (this._ammoPromise) {
            return this._ammoPromise;
        }
        this._ammoPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/vendor/ammo/ammo.wasm.js';
            script.async = true;
            script.onload = async () => {
                try {
                    const factory = window.Ammo;
                    const ammo = typeof factory === 'function' ? await factory() : factory;
                    window.Ammo = ammo;
                    console.log('[SceneManager] Ammo.js ready for MMD physics');
                    resolve(ammo);
                } catch (err) {
                    reject(err);
                }
            };
            script.onerror = () => reject(new Error('Ammo.js 加载失败'));
            document.head.appendChild(script);
        });
        return this._ammoPromise;
    }

    async _setupPhysics(mesh) {
        const data = mesh.geometry?.userData?.MMD;
        const rigidBodies = data?.rigidBodies ?? [];
        if (!rigidBodies.length) {
            console.log('[SceneManager] No PMX rigid bodies; physics skipped');
            return;
        }
        const baseScale = mesh.scale.clone();
        const basePosition = mesh.position.clone();
        const baseQuaternion = mesh.quaternion.clone();
        try {
            await this._loadAmmo();
            this.physics = new MMDPhysics(mesh, rigidBodies, data.constraints ?? [], {
                gravity: new THREE.Vector3(0, -9.8 * 10, 0),
                maxStepNum: 2,
            });
            this.physics.warmup(12);
            this._physicsBoneNames = this._collectPhysicsBones(mesh, rigidBodies);
            console.log(`[SceneManager] MMD physics ready: bodies=${rigidBodies.length}, constraints=${data.constraints?.length ?? 0}, dynamicBones=${this._physicsBoneNames.size}`);
        } catch (err) {
            // MMDPhysics temporarily unsets mesh scale during initialization.
            // Restore it if the second model's physics data is incomplete.
            this.physics = null;
            this._physicsBoneNames = new Set();
            mesh.scale.copy(baseScale);
            mesh.position.copy(basePosition);
            mesh.quaternion.copy(baseQuaternion);
            mesh.updateMatrixWorld(true);
            console.warn('[SceneManager] MMD physics unavailable:', err.message);
        }
    }

    _collectPhysicsBones(mesh, rigidBodies) {
        const names = new Set();
        const bones = mesh.skeleton?.bones ?? [];
        rigidBodies.forEach((body) => {
            // Type 0 bodies are kinematic and still follow the bones. Types 1/2
            // are simulated, so their bone tracks must not fight the solver.
            if (body.type === 0 || body.boneIndex == null || body.boneIndex < 0) return;
            const bone = bones[body.boneIndex];
            if (bone?.name) names.add(bone.name);
        });
        return names;
    }

    _filterPhysicsTracks(clip) {
        if (!clip?.tracks?.length || !this._physicsBoneNames?.size) return clip;
        clip.tracks = clip.tracks.filter((track) => {
            const dot = track.name.lastIndexOf('.');
            const boneName = dot > 0 ? track.name.slice(0, dot) : track.name;
            return !this._physicsBoneNames.has(boneName);
        });
        return clip;
    }

    _restoreModelTransform(mesh) {
        if (!mesh) return;
        if (this._modelBaseScale) mesh.scale.copy(this._modelBaseScale);
        if (this._modelBasePosition) mesh.position.copy(this._modelBasePosition);
        if (this._modelBaseQuaternion) mesh.quaternion.copy(this._modelBaseQuaternion);
        mesh.updateMatrixWorld(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // Character loading
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load a character by type key.
     *
     * @param {'yaoguang'|'jingliu'} characterType
     * @param {function} [onProgress] — 0–100 callback
     * @returns {Promise<void>}
     */
    async loadCharacter(characterType, onProgress) {
        const path = this.characterPaths.get(characterType);
        if (!path) {
            const msg = `Unknown character: "${characterType}" — no model path configured`;
            console.error(`[SceneManager] ${msg}`);
            throw new Error(msg);
        }

        console.log(`[SceneManager] loadCharacter("${characterType}") — path: ${path}`);

        try {
            const { mesh, bones, morphTargets } = await this.modelLoader.loadModel(path, onProgress);

            const mmdData = mesh.geometry?.userData?.MMD;
            console.log(`[SceneManager] MMD physics data: rigidBodies=${mmdData?.rigidBodies?.length ?? 0}, constraints=${mmdData?.constraints?.length ?? 0}`);

            console.log(`[SceneManager] Model loaded for "${characterType}" — computing bounding box...`);

            // ── Auto-scale model to fit view ─────────────────────────
            const bbox = new THREE.Box3().setFromObject(mesh);
            const size = new THREE.Vector3();
            bbox.getSize(size);
            const modelHeight = size.y;
            console.log(`[SceneManager] Model raw size: w=${size.x.toFixed(2)} h=${size.y.toFixed(2)} d=${size.z.toFixed(2)}`);

            // Target height: ~2.0 world units for chest-up framing
            const targetHeight = 2.0;
            const scale = targetHeight / Math.max(modelHeight, 0.01);
            mesh.scale.setScalar(scale);
            console.log(`[SceneManager] Model scaled by ${scale.toFixed(3)}x → height ~${(modelHeight * scale).toFixed(2)}`);

            // Center model horizontally at origin, feet at y≈0
            const center = new THREE.Vector3();
            bbox.getCenter(center);
            mesh.position.set(-center.x * scale, -bbox.min.y * scale, 0);
            console.log(`[SceneManager] Model positioned at (${mesh.position.x.toFixed(2)}, ${mesh.position.y.toFixed(2)}, ${mesh.position.z.toFixed(2)})`);

            this._modelBaseScale = mesh.scale.clone();
            this._modelBasePosition = mesh.position.clone();
            this._modelBaseQuaternion = mesh.quaternion.clone();

            mesh.castShadow = true;
            mesh.receiveShadow = true;
            mesh.traverse((child) => {
                if (child.isMesh) {
                    child.castShadow = true;
                    child.receiveShadow = true;
                }
            });

            // Enhance MMD materials for vibrant colors
            this._enhanceMaterials(mesh);

            // Adjust camera to frame the scaled model
            const cameraDist = targetHeight * 1.8;
            const cameraY = targetHeight * 0.55;
            this.camera.position.set(0, cameraY, cameraDist);
            this.camera.lookAt(0, targetHeight * 0.45, 0);
            console.log(`[SceneManager] Camera repositioned: (0, ${cameraY.toFixed(2)}, ${cameraDist.toFixed(2)})`);

            this.scene.add(mesh);
            this.currentMesh = mesh;
            this.currentCharacter = characterType;

            // PMX rigid bodies drive skirts, ribbons and hair; wait for Ammo
            // once, then hand the bones back to the normal animation loop.
            await this._setupPhysics(mesh);
            // MMDPhysics rescales the mesh internally; make the intended
            // normalized transform authoritative before the first render.
            this._restoreModelTransform(mesh);

            // Initialize expression engine
            this.expressionEngine = new ExpressionEngine(mesh, morphTargets);
            this.expressionEngine.setEmotion('neutral');

            // Initialize motion engine
            this.motionEngine = new MotionEngine(mesh, bones, morphTargets, this.expressionEngine);

            // Initialize LipSync
            this.lipSync = new LipSync(mesh, morphTargets);
            console.log('[SceneManager] LipSync created');

            // VMD idle disabled — VMD animations include hip movement that shakes lower body
            // Using pure procedural idle: head look-around + blink + micro-expressions
            // All lower body bones remain at rest pose

            console.log(`[SceneManager] Character "${characterType}" fully initialized and ready`);

        } catch (err) {
            console.error(`[SceneManager] Failed to load character "${characterType}":`, err);
            if (this.options.onError) {
                this.options.onError(err);
            }
            throw err;
        }
    }

    /**
     * Switch from current character to another. Unloads old, loads new.
     *
     * @param {'yaoguang'|'jingliu'} characterType
     * @param {function} [onProgress]
     * @returns {Promise<void>}
     */
    async switchCharacter(characterType, onProgress) {
        if (this.currentCharacter === characterType) {
            console.log(`[SceneManager] switchCharacter: already showing "${characterType}" — skipping`);
            return;
        }

        console.log(`[SceneManager] switchCharacter: "${this.currentCharacter}" → "${characterType}"`);

        // Dispose current engines and model
        this._unloadCurrentCharacter();

        // Load new
        await this.loadCharacter(characterType, onProgress);
    }

    /**
     * Unload the currently displayed character (engines + mesh).
     */
    _unloadCurrentCharacter() {
        this.stopFbxMotion();
        this.stopVmd();
        this.stopIdleMotion();
        this.stopGestureVmd();
        this._stopLipSyncPlayback();
        if (this.lipSync) {
            this.lipSync.disconnect();
            this.lipSync = null;
            console.log('[SceneManager] LipSync disconnected');
        }

        if (this.motionEngine) {
            this.motionEngine.dispose();
            this.motionEngine = null;
            console.log('[SceneManager] MotionEngine disposed');
        }

        this.expressionEngine = null;

        if (this.physics) {
            this.physics = null;
            console.log('[SceneManager] MMD physics released');
        }
        this._modelBaseScale = null;
        this._modelBasePosition = null;
        this._modelBaseQuaternion = null;
        this._physicsBoneNames = new Set();
        this._physicsAccumulator = 0;
        this._idleClipCache = null;
        this._idleClipCacheUrl = '';

        if (this.currentMesh) {
            this.modelLoader.unloadModel(this.currentMesh);
            this.currentMesh = null;
            console.log('[SceneManager] Current mesh unloaded');
        }

        this.currentCharacter = null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Expression & Motion delegation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set facial expression.
     *
     * @param {'neutral'|'happy'|'sad'} emotion
     */
    setEmotion(emotion) {
        if (!this.expressionEngine) {
            console.warn('[SceneManager] setEmotion called but no ExpressionEngine active');
            return;
        }
        console.log(`[SceneManager] setEmotion("${emotion}")`);
        this.expressionEngine.setEmotion(emotion);
    }

    /**
     * Trigger a motion animation.
     *
     * @param {'greet'|'nod'} type
     * @param {string} [text]
     */
    triggerMotion(type, text) {
        if (this.motionEngine) {
            this.motionEngine.trigger(type, text);
        }
    }

    /** Play an expressive procedural gesture (wave, bow, think...). */
    playGesture(name) {
        if (!this.motionEngine) return false;
        if (this._gestureVmdMixer) this.stopGestureVmd();
        if (this._idleVmdMixer) this.stopIdleMotion();
        if (this._vmdMixer) this.stopVmd();
        if (this._fbxMixer) this.stopFbxMotion();
        return this.motionEngine.playGesture(name);
    }

    /**
     * Play a one-shot upper-body VMD gesture from a downloaded motion pack.
     * The caller decides when to resume the idle loop after the returned
     * duration (plus a small blend-out).
     */
    async playGestureVmd(url, options = {}) {
        if (!this.currentMesh || !this.motionEngine) {
            throw new Error('模型尚未加载完成');
        }
        const token = ++this._gestureVmdToken;
        const raw = await this.modelLoader.loadAnimationClip(url, this.currentMesh);
        if (token !== this._gestureVmdToken) return 0;
        const rootBones = ['センター', 'グルーブ', '全ての親', '左足IK', '右足IK', '左足ＩＫ', '右足ＩＫ'];
        const headBones = ['頭', '首', 'neck', 'head'];
        const lowerBodyBones = [
            '下半身', '腰', '左足', '右足', '左ひざ', '右ひざ',
            '左足首', '右足首', '左つま先', '右つま先',
            '左足D', '右足D', '左ひざD', '右ひざD',
            '左足首D', '右足首D', '左足先EX', '右足先EX',
        ];
        raw.tracks = raw.tracks.filter((track) => {
            if (track.name.endsWith('.position')) return false;
            if (track.name.includes('morphTargetInfluences')) return false;
            if (rootBones.some((bone) => track.name.includes(bone))) return false;
            if (headBones.some((bone) => track.name.includes(bone))) return false;
            if (lowerBodyBones.some((bone) => track.name.includes(bone))) return false;
            return true;
        });
        this._filterPhysicsTracks(raw);
        raw.resetDuration();
        if (!raw.tracks.length || raw.duration <= 0) {
            throw new Error('VMD 动作没有可用的上半身轨道');
        }
        const maxSeconds = Number(options.maxSeconds || 15);
        let clip = raw;
        if (clip.duration > maxSeconds) {
            const fps = 30;
            clip = THREE.AnimationUtils.subclip(clip, `gesture_${token}`, 0, Math.max(1, Math.floor(maxSeconds * fps)), fps);
        }
        if (token !== this._gestureVmdToken) return 0;
        this.stopIdleMotion();
        if (this._vmdMixer) this.stopVmd();
        if (this._fbxMixer) this.stopFbxMotion();
        this.stopGestureVmd();
        this._gestureVmdMixer = new THREE.AnimationMixer(this.currentMesh);
        this._gestureVmdAction = this._gestureVmdMixer.clipAction(clip);
        this._gestureVmdAction.setLoop(THREE.LoopOnce, 1);
        this._gestureVmdAction.clampWhenFinished = true;
        this._gestureVmdAction.play();
        this._gestureVmdActive = true;
        if (this.motionEngine) this.motionEngine.enabled = false;
        this._gestureHeadBase = this.motionEngine?.head?.quaternion.clone() || null;
        this._gestureVmdMixer.addEventListener('finished', () => {
            this._gestureVmdActive = false;
        });
        console.log(`[SceneManager] Gesture VMD playing: ${url}, ${clip.duration.toFixed(2)}s`);
        return clip.duration;
    }

    stopGestureVmd() {
        this._gestureVmdToken += 1;
        if (this._gestureVmdMixer) {
            this._gestureVmdMixer.stopAllAction();
            if (this.currentMesh) this._gestureVmdMixer.uncacheRoot(this.currentMesh);
            this._gestureVmdMixer = null;
        }
        this._gestureVmdAction = null;
        this._gestureVmdActive = false;
        this._gestureHeadBase = null;
        if (this.motionEngine) this.motionEngine.enabled = true;
    }

    // ─── Local conversational idle VMD ───────────────────────────

    _idleVmdMixer = null;
    _idleVmdAction = null;
    _idleVmdToken = 0;
    _idleVmdActive = false;
    _idleVmdUrl = '';
    _idleClipCache = null;
    _idleClipCacheUrl = '';
    _idleSpeechTime = 0;
    _idleSpeechQuaternion = null;
    _idleVmdUrls = [];
    _idleVmdIndex = 0;
    _idleBlend = null;
    _idleBlendElapsed = 0;
    _idleBlendDuration = 0.9;

    _gestureVmdMixer = null;
    _gestureVmdAction = null;
    _gestureVmdActive = false;
    _gestureVmdToken = 0;
    _gestureHeadBase = null;

    async startIdleMotion(urls = []) {
        if (!this.currentMesh) return false;
        this._idleVmdUrls = urls;
        this._idleVmdIndex = 0;
        for (const url of urls) {
            try {
                await this.playIdleMotion(url);
                return true;
            } catch (err) {
                console.warn(`[SceneManager] Idle motion unavailable (${url}): ${err.message}`);
            }
        }
        return false;
    }

    async playIdleMotion(url) {
        if (!this.currentMesh || !this.motionEngine) return;
        const token = ++this._idleVmdToken;
        let clip;
        if (this._idleClipCache && this._idleClipCacheUrl === url) {
            clip = this._idleClipCache;
        } else {
            const raw = await this.modelLoader.loadAnimationClip(url, this.currentMesh);
            if (token !== this._idleVmdToken) return;
            const rootBones = ['センター', 'グルーブ', '全ての親', '左足IK', '右足IK', '左足ＩＫ', '右足ＩＫ'];
            const lowerBodyBones = [
                '下半身', '腰', '左足', '右足', '左ひざ', '右ひざ',
                '左足首', '右足首', '左つま先', '右つま先',
                '左足D', '右足D', '左ひざD', '右ひざD',
                '左足首D', '右足首D', '左足先EX', '右足先EX',
            ];
            raw.tracks = raw.tracks.filter((track) => {
                if (track.name.endsWith('.position')) return false;
                if (track.name.includes('morphTargetInfluences')) return false;
                if (rootBones.some((bone) => track.name.includes(bone))) return false;
                if (lowerBodyBones.some((bone) => track.name.includes(bone))) return false;
                return true;
            });
            this._filterPhysicsTracks(raw);
            raw.resetDuration();
            if (!raw.tracks.length || raw.duration <= 0) {
                throw new Error('idle clip has no upper-body tracks');
            }
            clip = raw;
            this._idleClipCache = clip;
            this._idleClipCacheUrl = url;
        }
        if (token !== this._idleVmdToken) return;
        if (this._gestureVmdMixer || this.motionEngine?.gesture || this._vmdMixer || this._fbxMixer) return;
        this._captureIdleBlend();
        this.stopIdleMotion(false);
        this._idleVmdMixer = new THREE.AnimationMixer(this.currentMesh);
        this._idleVmdAction = this._idleVmdMixer.clipAction(clip);
        this._idleVmdAction.setLoop(THREE.LoopRepeat, Infinity);
        this._idleVmdAction.clampWhenFinished = false;
        this._idleVmdAction.play();
        this._idleVmdActive = true;
        this._idleVmdUrl = url;
        this._idleSpeechTime = 0;
        this.motionEngine.enabled = false;
        console.log(`[SceneManager] Idle VMD playing: ${url}, ${clip.duration.toFixed(1)}s`);
    }

    stopIdleMotion(enableMotion = true) {
        this._idleVmdToken += 1;
        if (this._idleVmdMixer) {
            this._idleVmdMixer.stopAllAction();
            if (this.currentMesh) this._idleVmdMixer.uncacheRoot(this.currentMesh);
            this._idleVmdMixer = null;
        }
        this._idleVmdAction = null;
        this._idleVmdActive = false;
        this._idleSpeechTime = 0;
        this._idleBlend = null;
        if (enableMotion && this.motionEngine) this.motionEngine.enabled = true;
    }

    _captureIdleBlend() {
        const skeleton = this.currentMesh?.skeleton;
        if (!skeleton) return;
        this._idleBlend = skeleton.bones.map((bone) => [bone, bone.quaternion.clone()]);
        this._idleBlendElapsed = 0;
    }

    _applyIdleBlend(delta) {
        if (!this._idleBlend) return;
        this._idleBlendElapsed += delta;
        const w = Math.min(1, this._idleBlendElapsed / this._idleBlendDuration);
        const eased = w * w * (3 - 2 * w);
        for (const [bone, start] of this._idleBlend) {
            bone.quaternion.slerp(start, 1 - eased);
        }
        if (w >= 1) this._idleBlend = null;
    }

    resumeIdleMotion() {
        if (this._fbxMixer || this._vmdMixer || this._idleVmdMixer) return;
        // Never let an idle loop cut a gesture that is still playing.
        if (this.motionEngine?.gesture) return;
        if (this._gestureVmdMixer && this._gestureVmdActive) return;
        if (this._gestureVmdMixer) this.stopGestureVmd();
        const urls = this._idleVmdUrls?.length ? this._idleVmdUrls : (this._idleVmdUrl ? [this._idleVmdUrl] : []);
        if (!urls.length) return;
        if (urls.length > 1) this._idleVmdIndex = (this._idleVmdIndex + 1) % urls.length;
        const ordered = [...urls.slice(this._idleVmdIndex), ...urls.slice(0, this._idleVmdIndex)];
        const tryNext = (index) => {
            if (index >= ordered.length) return;
            void this.playIdleMotion(ordered[index]).catch(() => tryNext(index + 1));
        };
        tryNext(0);
    }

    _applyIdleSpeechOverlay(delta) {
        if (!this.motionEngine?.speaking || !this.motionEngine.head) return;
        if (this._gestureVmdActive && this._gestureHeadBase) {
            this.motionEngine.head.quaternion.copy(this._gestureHeadBase);
        }
        this._idleSpeechTime += delta;
        const q = this._idleSpeechQuaternion || (this._idleSpeechQuaternion = new THREE.Quaternion());
        const nod = Math.sin(this._idleSpeechTime * 3.1) * 0.018;
        q.setFromAxisAngle(new THREE.Vector3(1, 0, 0), nod);
        this.motionEngine.head.quaternion.multiply(q);
    }

    // ─── Hunyuan FBX retargeting ─────────────────────────────────

    _fbxMixer = null;
    _fbxAction = null;

    /**
     * Load a Hunyuan FBX and retarget its skeleton animation onto the current PMX.
     *
     * The source uses SMPL-style names (Pelvis / Spine1 / L_Shoulder / L_Elbow...).
     * SkeletonUtils.retargetClip converts the world-space motion to MMD bone
     * tracks, so the generated motion can drive 爻光 / 镜流 directly.
     */
    async playRetargetedFbx(url, options = {}) {
        if (!this.currentMesh || !this.motionEngine) {
            throw new Error('PMX 模型尚未加载完成');
        }
        if (this._gestureVmdMixer) this.stopGestureVmd();
        if (this._idleVmdMixer) this.stopIdleMotion();
        if (this._vmdMixer) this.stopVmd();

        const fbx = await this.modelLoader.loadFbx(url, options.onProgress);
        let sourceMesh = null;
        fbx.traverse((child) => {
            if (!sourceMesh && child.isSkinnedMesh && child.skeleton && child.skeleton.bones.length) {
                sourceMesh = child;
            }
        });
        if (!sourceMesh) {
            throw new Error('FBX 中没有可用的骨骼');
        }
        if (!fbx.animations || !fbx.animations.length) {
            throw new Error('FBX 中没有动画轨道');
        }

        // SkeletonUtils needs source.skeleton and a mixer root that contains all bones.
        fbx.skeleton = sourceMesh.skeleton;

        const names = {
            // Keep the pelvis/legs at rest so large gestures never throw the
            // lower body around. Only the dialogue upper body is retargeted.
            '上半身': 'Spine1',
            '上半身2': 'Spine2',
            '上半身3': 'Spine3',
            '首': 'Head',
            // arms
            '左肩': 'L_Collar',
            '左腕': 'L_Shoulder',
            '左ひじ': 'L_Elbow',
            '左手首': 'L_Wrist',
            '右肩': 'R_Collar',
            '右腕': 'R_Shoulder',
            '右ひじ': 'R_Elbow',
            '右手首': 'R_Wrist',
        };

        const savedPosition = this.currentMesh.position.clone();
        const savedQuaternion = this.currentMesh.quaternion.clone();
        const savedScale = this.currentMesh.scale.clone();
        const savedMatrixAutoUpdate = this.currentMesh.matrixAutoUpdate;

        const clip = retargetClip(this.currentMesh, fbx, fbx.animations[0], {
            fps: 30,
            names,
            hip: 'Pelvis',
            useFirstFramePosition: false,
        });

        // SkeletonUtils temporarily normalizes the target root matrix while
        // sampling. Restore the PMX stage transform afterwards.
        this.currentMesh.position.copy(savedPosition);
        this.currentMesh.quaternion.copy(savedQuaternion);
        this.currentMesh.scale.copy(savedScale);
        this.currentMesh.matrixAutoUpdate = savedMatrixAutoUpdate;
        if (this.currentMesh.skeleton) {
            this.currentMesh.skeleton.pose();
        }
        this.currentMesh.updateMatrixWorld(true);
        console.log('[SceneManager] Retarget tracks:', clip.tracks.slice(0, 12).map((track) => track.name).join(', '));

        // Keep the character centered; ignore root translation tracks.
        clip.tracks = clip.tracks.filter((track) => !track.name.endsWith('.position'));
        if (!clip.tracks.length) {
            throw new Error('重定向后没有可用的 MMD 骨骼轨道');
        }

        this.stopVmd();
        this.stopFbxMotion();

        this._fbxMixer = new THREE.AnimationMixer(this.currentMesh);
        this._fbxAction = this._fbxMixer.clipAction(clip);
        this._fbxAction.setLoop(THREE.LoopOnce, 1);
        this._fbxAction.clampWhenFinished = true;
        this._fbxAction.play();
        if (this.motionEngine) this.motionEngine.enabled = false;

        this._fbxMixer.addEventListener('finished', () => {
            this.stopFbxMotion();
            if (options.onFinished) options.onFinished();
        });

        console.log(`[SceneManager] Retargeted FBX playing: ${clip.tracks.length} tracks, ${clip.duration.toFixed(1)}s`);
        return { duration: clip.duration, tracks: clip.tracks.length };
    }

    /** Stop a retargeted FBX animation and return to procedural idle. */
    stopFbxMotion() {
        if (this._fbxMixer) {
            this._fbxMixer.stopAllAction();
            if (this.currentMesh) this._fbxMixer.uncacheRoot(this.currentMesh);
            this._fbxMixer = null;
        }
        this._fbxAction = null;
        if (this.motionEngine) this.motionEngine.enabled = true;
    }

    // ─── VMD Animation ────────────────────────────────────────────

    /** VMD animation mixer */
    _vmdMixer = null;
    _vmdAction = null;
    _vmdToken = 0;
    _vmdElapsed = 0;
    _vmdHipBones = null;
    _vmdHipRest = null;
    _vmdLegTargets = null;
    _vmdTorso = null;
    _vmdTorsoRest = null;
    _vmdSwayQuaternion = null;

    /**
     * Load and loop a VMD motion module.
     *
     * The dance UI uses upper-body tracks only: root translation, pelvis and
     * leg tracks are removed so both feet stay fixed. A small procedural hip
     * sway is layered on top, with the thighs/ankles counter-rotated so the
     * butt moves while the feet remain planted.
     */
    async playVmd(url, options = {}) {
        if (!this.currentMesh || !this.motionEngine) {
            throw new Error('模型尚未加载完成');
        }
        if (this._gestureVmdMixer) this.stopGestureVmd();
        if (this._idleVmdMixer) this.stopIdleMotion();
        if (this._fbxMixer) this.stopFbxMotion();
        const token = ++this._vmdToken;
        const maxSeconds = options.maxSeconds || 10;
        console.log(`[SceneManager] Loading VMD motion: ${url}`);

        const rawClip = await this.modelLoader.loadAnimationClip(url, this.currentMesh);
        if (token !== this._vmdToken) return null;

        const rootBones = ['センター', 'グルーブ', '全ての親', '左足IK', '右足IK', '左足ＩＫ', '右足ＩＫ'];
        const lowerBodyBones = [
            '下半身', '腰', '左足', '右足', '左ひざ', '右ひざ',
            '左足首', '右足首', '左つま先', '右つま先',
            '左足D', '右足D', '左ひざD', '右ひざD',
            '左足首D', '右足首D', '左足先EX', '右足先EX',
        ];
        rawClip.tracks = rawClip.tracks.filter((track) => {
            if (track.name.endsWith('.position')) return false;
            if (track.name.includes('morphTargetInfluences')) return false;
            if (rootBones.some((bone) => track.name.includes(bone))) return false;
            if (lowerBodyBones.some((bone) => track.name.includes(bone))) return false;
            return true;
        });
        this._filterPhysicsTracks(rawClip);
        rawClip.resetDuration();

        let clip = rawClip;
        if (clip.duration > maxSeconds) {
            const fps = 30;
            const endFrame = Math.max(2, Math.floor(maxSeconds * fps));
            clip = THREE.AnimationUtils.subclip(clip, 'vmd_loop', 0, endFrame, fps);
        }
        if (!clip.tracks.length || clip.duration <= 0) {
            throw new Error('动作模组没有可用的上半身轨道');
        }

        this.stopVmd();
        this._vmdMixer = new THREE.AnimationMixer(this.currentMesh);
        this._vmdAction = this._vmdMixer.clipAction(clip);
        this._vmdAction.setLoop(THREE.LoopRepeat, Infinity);
        this._vmdAction.clampWhenFinished = false;
        this._vmdAction.play();
        this._vmdActive = true;
        this._vmdElapsed = 0;
        const skeleton = this.currentMesh.skeleton;
        this._vmdHipBones = skeleton ? {
            hip: skeleton.getBoneByName('下半身') || null,
            lThigh: skeleton.getBoneByName('左足') || null,
            rThigh: skeleton.getBoneByName('右足') || null,
            lKnee: skeleton.getBoneByName('左ひざ') || null,
            rKnee: skeleton.getBoneByName('右ひざ') || null,
            lAnkle: skeleton.getBoneByName('左足首') || null,
            rAnkle: skeleton.getBoneByName('右足首') || null,
        } : null;
        this._vmdTorso = skeleton ? skeleton.getBoneByName('上半身') || null : null;
        this._vmdTorsoRest = this._vmdTorso ? this._vmdTorso.quaternion.clone() : null;
        this._vmdHipRest = {};
        this._vmdLegTargets = {};
        this.currentMesh.updateMatrixWorld(true);
        if (this._vmdHipBones) {
            for (const [key, bone] of Object.entries(this._vmdHipBones)) {
                if (bone) this._vmdHipRest[key] = bone.quaternion.clone();
            }
            for (const [side, ankleKey] of [['l', 'lAnkle'], ['r', 'rAnkle']]) {
                const ankle = this._vmdHipBones[ankleKey];
                if (!ankle) continue;
                this._vmdLegTargets[side] = {
                    position: ankle.getWorldPosition(new THREE.Vector3()),
                    quaternion: ankle.getWorldQuaternion(new THREE.Quaternion()),
                };
            }
        }
        if (this.motionEngine) this.motionEngine.enabled = false;

        console.log(`[SceneManager] VMD playing: tracks=${clip.tracks.length}, duration=${clip.duration.toFixed(1)}s`);
        return { duration: clip.duration, tracks: clip.tracks.length };
    }

    /** Stop the current VMD motion and return to procedural idle. */
    stopVmd() {
        this._vmdToken += 1;
        if (this._vmdMixer) {
            this._vmdMixer.stopAllAction();
            if (this.currentMesh) this._vmdMixer.uncacheRoot(this.currentMesh);
            this._vmdMixer = null;
        }
        this._vmdAction = null;
        this._vmdActive = false;
        if (this._vmdHipBones && this._vmdHipRest) {
            for (const [key, bone] of Object.entries(this._vmdHipBones)) {
                const rest = this._vmdHipRest[key];
                if (bone && rest) bone.quaternion.copy(rest);
            }
        }
        this._vmdHipBones = null;
        this._vmdHipRest = null;
        this._vmdLegTargets = null;
        this._vmdTorso = null;
        this._vmdTorsoRest = null;
        this._vmdSwayQuaternion = null;
        if (this.motionEngine) this.motionEngine.enabled = true;
    }

    /**
     * Let the pelvis follow the torso, then solve a two-bone IK for both legs
     * so the ankles stay at the exact world position captured at dance start.
     */
    _applyVmdHipMotion(delta) {
        const bones = this._vmdHipBones;
        const rest = this._vmdHipRest;
        const targets = this._vmdLegTargets;
        if (!bones || !rest || !targets) return;
        this._vmdElapsed += delta;
        const q = this._vmdSwayQuaternion || (this._vmdSwayQuaternion = new THREE.Quaternion());
        const axes = {
            x: new THREE.Vector3(1, 0, 0),
            y: new THREE.Vector3(0, 1, 0),
            z: new THREE.Vector3(0, 0, 1),
        };
        const euler = new THREE.Euler();
        if (this._vmdTorso && this._vmdTorsoRest) {
            // The hand-dance VMD does not animate the torso, so create a small
            // upper-body sway and let the pelvis follow it. Reset to rest first
            // so the procedural offset cannot accumulate frame by frame.
            this._vmdTorso.quaternion.copy(this._vmdTorsoRest);
            const upperSwayY = Math.sin(this._vmdElapsed * 2.0) * 0.06;
            const upperSwayZ = Math.sin(this._vmdElapsed * 1.7 + 0.6) * 0.022;
            this._vmdTorso.quaternion.multiply(q.setFromAxisAngle(axes.y, upperSwayY));
            this._vmdTorso.quaternion.multiply(q.setFromAxisAngle(axes.z, upperSwayZ));
            euler.setFromQuaternion(
                this._vmdTorsoRest.clone().invert().multiply(this._vmdTorso.quaternion),
                'XYZ',
            );
        }
        const clamp = THREE.MathUtils.clamp;
        const swayX = 0;
        const swayY = clamp(euler.y * 0.75, -0.09, 0.09);
        const swayZ = 0;

        if (bones.hip) {
            bones.hip.quaternion.copy(rest.hip);
            bones.hip.quaternion.multiply(q.setFromAxisAngle(axes.x, swayX));
            bones.hip.quaternion.multiply(q.setFromAxisAngle(axes.y, swayY));
            bones.hip.quaternion.multiply(q.setFromAxisAngle(axes.z, swayZ));
        }
        for (const key of ['lThigh', 'rThigh', 'lKnee', 'rKnee', 'lAnkle', 'rAnkle']) {
            if (bones[key] && rest[key]) bones[key].quaternion.copy(rest[key]);
        }
        this.currentMesh.updateMatrixWorld(true);
        if (bones.lThigh && bones.lKnee && bones.lAnkle && targets.l) {
            this._solveLegIk(bones.lThigh, bones.lKnee, bones.lAnkle, targets.l);
        }
        if (bones.rThigh && bones.rKnee && bones.rAnkle && targets.r) {
            this._solveLegIk(bones.rThigh, bones.rKnee, bones.rAnkle, targets.r);
        }
        this.currentMesh.updateMatrixWorld(true);
    }

    _solveLegIk(thigh, knee, ankle, target) {
        const vEnd = new THREE.Vector3();
        const vTarget = new THREE.Vector3();
        const vRoot = new THREE.Vector3();
        const qWorld = new THREE.Quaternion();
        const qParent = new THREE.Quaternion();
        const qDelta = new THREE.Quaternion();
        for (let pass = 0; pass < 100; pass += 1) {
            for (const bone of [knee, thigh]) {
                bone.updateWorldMatrix(true, false);
                bone.getWorldPosition(vRoot);
                ankle.getWorldPosition(vEnd);
                vEnd.sub(vRoot);
                vTarget.copy(target.position).sub(vRoot);
                if (vEnd.lengthSq() < 1e-8 || vTarget.lengthSq() < 1e-8) continue;
                vEnd.normalize();
                vTarget.normalize();
                qWorld.setFromUnitVectors(vEnd, vTarget);
                bone.parent.getWorldQuaternion(qParent);
                qDelta.copy(qParent).invert().multiply(qWorld).multiply(qParent);
                bone.quaternion.premultiply(qDelta);
                bone.updateMatrixWorld(true);
            }
        }
        // Keep the foot's world orientation exactly as it was at dance start.
        ankle.parent.getWorldQuaternion(qParent);
        ankle.quaternion.copy(qParent.invert().multiply(target.quaternion));
        ankle.updateMatrixWorld(true);
    }

    async _loadIdleAnimation(characterType) {
        // Idle is procedural; VMD modules are opt-in via playVmd().
        return null;
    }

    async _playTalkGesture() {
        if (this.motionEngine) this.motionEngine.playGesture('nod');
    }

    // ═══════════════════════════════════════════════════════════════
    // Audio / LipSync
    // ═══════════════════════════════════════════════════════════════

    /**
     * Connect a remote audio track for lip-sync animation.
     *
     * Creates a LipSync instance that reads frequency data from the
     * audio track and drives mouth morph targets on the current mesh.
     *
     * NOTE: This replaces the old Base64 PCM decoding + AudioBuffer
     * queue approach. Audio arrives solely through WebRTC AudioTrack.
     *
     * @param {MediaStreamTrack} audioTrack — remote WebRTC audio track
     */
    connectAudio(audioTrack) {
        console.log('[SceneManager] connectAudio() called');

        if (!audioTrack) {
            console.error('[SceneManager] connectAudio: audioTrack is null/undefined');
            return;
        }

        if (!this.currentMesh) {
            console.warn('[SceneManager] connectAudio: no current mesh — cannot create LipSync');
            return;
        }

        if (!this.modelLoader || !this.currentMesh) {
            console.warn('[SceneManager] connectAudio: mesh not loaded yet');
            return;
        }

        // Collect morph targets from ALL child meshes
        const morphTargets = {};
        this.currentMesh.traverse((child) => {
            if (child.isSkinnedMesh && child.morphTargetDictionary) {
                Object.entries(child.morphTargetDictionary).forEach(([name, index]) => {
                    if (!morphTargets[name]) {
                        morphTargets[name] = { index, mesh: child };
                    }
                });
            }
        });
        console.log(`[SceneManager] Morph targets for LipSync: ${Object.keys(morphTargets).length} entries`);

        try {
            // Dispose previous LipSync if it exists
            if (this.lipSync) {
                console.log('[SceneManager] Disconnecting previous LipSync');
                this.lipSync.disconnect();
                this.lipSync = null;
            }

            // Create and connect new LipSync
            this.lipSync = new LipSync(this.currentMesh, morphTargets);
            this.lipSync.connect(audioTrack);
            console.log('[SceneManager] LipSync created and connected to audio track');
        } catch (err) {
            console.error('[SceneManager] Failed to create/connect LipSync:', err);
            this.lipSync = null;
        }
    }

    /** Buffer for timed lip-sync frame playback */
    _lipFrameBuffer = [];
    _lipFrameInterval = null;
    _lipFrameIndex = 0;

    /**
     * Buffer a server-computed lip-sync frame for timed playback.
     */
    enqueueLipFrame(mouthOpen, mouthWide) {
        this._lipFrameBuffer.push({ mouthOpen, mouthWide });
        if (this._lipFrameBuffer.length === 1) {
            console.log('[SceneManager] Lip frame buffering started (first frame)');
        }
    }

    /**
     * Start playing buffered lip-sync frames at 30fps.
     */
    startLipSyncPlayback(totalFrames) {
        console.log(`[SceneManager] startLipSyncPlayback: ${this._lipFrameBuffer.length} buffered frames`);
        // Only stop the interval, don't clear the buffer!
        if (this._lipFrameInterval) {
            clearInterval(this._lipFrameInterval);
            this._lipFrameInterval = null;
        }
        if (this.lipSync) {
            this.lipSync._applyCount = 0;
        }
        this._lipFrameIndex = 0;
        const fps = 30;
        const interval = 1000 / fps;
        const buffer = this._lipFrameBuffer; // local ref

        this._lipFrameInterval = setInterval(() => {
            if (this._lipFrameIndex < buffer.length) {
                const frame = buffer[this._lipFrameIndex];
                if (this.lipSync) {
                    this.lipSync.applyServerWeights(frame.mouthOpen, frame.mouthWide);
                }
                this._lipFrameIndex++;
            } else {
                if (this.lipSync) {
                    this.lipSync.applyServerWeights(0, 0);
                }
                this._stopLipSyncPlayback();
            }
        }, interval);
    }

    _stopLipSyncPlayback() {
        if (this._lipFrameInterval) {
            clearInterval(this._lipFrameInterval);
            this._lipFrameInterval = null;
        }
        this._lipFrameBuffer = []; // clear after playback complete
        this._lipFrameIndex = 0;
        if (this.lipSync) {
            this.lipSync._applyCount = 0;
        }
    }

    /**
     * Apply server-computed lip-sync weights directly to morph targets.
     * (For use with buffered/timed playback)
     */
    applyLipFrame(mouthOpen, mouthWide) {
        if (!this.lipSync) return;
        this.lipSync.applyServerWeights(mouthOpen, mouthWide);
    }

    async resumeAudio() {
        if (!this.lipSync) return;
        await this.lipSync.resume();
    }

    /**
     * Connect LipSync to an HTML <audio> element (for HTTP audio playback).
     */
    connectAudioElement(audioElement) {
        console.log('[SceneManager] connectAudioElement() called');
        if (!audioElement || !this.currentMesh) return;

        try {
            // Reuse existing LipSync if already created, just connect audio
            if (this.lipSync && this.lipSync.isConnected && !this.lipSync.isActive) {
                console.log('[SceneManager] Reconnecting existing LipSync to audio element');
                this.lipSync.connectFromElement(audioElement);
            } else if (!this.lipSync || !this.lipSync.isActive) {
                // Create fresh LipSync (but keep existing if it has morph targets resolved)
                const morphTargets = {};
                this.currentMesh.traverse((child) => {
                    if (child.isSkinnedMesh && child.morphTargetDictionary) {
                        Object.entries(child.morphTargetDictionary).forEach(([name, index]) => {
                            if (!morphTargets[name]) morphTargets[name] = { index, mesh: child };
                        });
                    }
                });
                if (this.lipSync) this.lipSync.disconnect();
                this.lipSync = new LipSync(this.currentMesh, morphTargets);
                this.lipSync.connectFromElement(audioElement);
            }
            console.log('[SceneManager] LipSync connected to audio element');
        } catch (err) {
            console.error('[SceneManager] Failed to connect LipSync to audio element:', err);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Render loop
    // ═══════════════════════════════════════════════════════════════

    _startLoop() {
        if (this.isRunning) return;
        this.isRunning = true;

        console.log('[SceneManager] Starting animation loop');

        const animate = () => {
            this.animationFrameId = requestAnimationFrame(animate);

            const delta = Math.min(this.clock.getDelta(), 0.1); // cap to avoid spiral

            // Update engines
            if (this._fbxMixer) {
                this._fbxMixer.update(delta);
            } else if (this._vmdMixer) {
                this._vmdMixer.update(delta);
                if (this._vmdActive) this._applyVmdHipMotion(delta);
            } else if (this._gestureVmdMixer) {
                this._gestureVmdMixer.update(delta);
                if (this._gestureVmdActive) this._applyIdleSpeechOverlay(delta);
                if (this.motionEngine?.updateFacial) this.motionEngine.updateFacial(delta);
            } else if (this._idleVmdMixer) {
                this._idleVmdMixer.update(delta);
                this._applyIdleBlend(delta);
                if (this._idleVmdActive) this._applyIdleSpeechOverlay(delta);
                if (this.motionEngine?.updateFacial) this.motionEngine.updateFacial(delta);
            } else if (this.motionEngine) {
                this.motionEngine.update(delta);
            }
            if (this.expressionEngine) {
                this.expressionEngine.update(delta);
            }
            if (this.lipSync) {
                this.lipSync.update(delta);
            }
            if (this.physics) {
                try {
                    // Fixed 60 Hz stepping is much more stable than feeding
                    // a 120+ FPS render delta straight into Bullet.
                    this._physicsAccumulator += Math.min(delta, 1 / 20);
                    let steps = 0;
                    while (this._physicsAccumulator >= 1 / 60 && steps < 2) {
                        this.physics.update(1 / 60);
                        this._physicsAccumulator -= 1 / 60;
                        steps += 1;
                    }
                    if (steps === 2) this._physicsAccumulator = 0;
                } catch (err) {
                    this._restoreModelTransform(this.currentMesh);
                    this.physics = null;
                    this._physicsAccumulator = 0;
                    console.warn('[SceneManager] MMD physics update failed:', err.message);
                }
            }

            // Render
            this.renderer.render(this.scene, this.camera);

            // FPS logging (every 10s)
            this.frameCount++;
            this.fpsLogTimer += delta;
            if (this.fpsLogTimer >= 10) {
                const fps = Math.round(this.frameCount / this.fpsLogTimer);
                console.log(`[SceneManager] FPS: ${fps}`);
                this.frameCount = 0;
                this.fpsLogTimer = 0;
            }
        };

        animate();
    }

    // ═══════════════════════════════════════════════════════════════
    // Resize
    // ═══════════════════════════════════════════════════════════════

    _onResize() {
        if (!this.canvas || !this.renderer || !this.camera) return;

        const width = this.canvas.clientWidth;
        const height = this.canvas.clientHeight;

        if (width === 0 || height === 0) return;

        console.log(`[SceneManager] Resize: ${width}x${height}`);

        this.renderer.setSize(width, height, false);
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
    }

    /** Public resize hook for Vue layout/fullscreen changes. */
    resize() {
        this._onResize();
    }

    // ═══════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════

    dispose() {
        console.log('[SceneManager] dispose() called');

        // Stop loop
        if (this.animationFrameId) {
            cancelAnimationFrame(this.animationFrameId);
            this.animationFrameId = null;
            this.isRunning = false;
            console.log('[SceneManager] Animation loop stopped');
        }

        // Unload character
        this._unloadCurrentCharacter();

        // Remove resize listener
        if (this._resizeHandler) {
            window.removeEventListener('resize', this._resizeHandler);
            this._resizeHandler = null;
            console.log('[SceneManager] Resize listener removed');
        }

        // Dispose scene
        if (this.renderer) {
            this.renderer.dispose();
            this.renderer = null;
            console.log('[SceneManager] Renderer disposed');
        }

        console.log('[SceneManager] Full disposal complete');
    }
}
