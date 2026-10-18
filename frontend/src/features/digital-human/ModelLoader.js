/**
 * ModelLoader — loads PMX models via Three.js MMDLoader.
 *
 * Responsibilities:
 * - Creates and configures one MMDLoader instance
 * - loadModel(modelPath, onProgress) → Promise<{mesh, bones, morphTargets}>
 * - unloadModel(mesh) → disposes geometry, materials, textures
 * - Extensive logging at every stage
 */

import * as THREE from 'three';
import { MMDLoader } from 'three/addons/loaders/MMDLoader.js';
import { FBXLoader } from 'three/addons/loaders/FBXLoader.js';

export class ModelLoader {
    constructor() {
        console.log('[ModelLoader] Initializing MMDLoader instance');
        this.loader = new MMDLoader();
        console.log('[ModelLoader] MMDLoader created successfully');
    }

    /**
     * Load a PMX model from the given path.
     *
     * @param {string} modelPath  - URL path to the .pmx file
     * @param {function} [onProgress]  - optional callback receiving 0–100 percentage
     * @returns {Promise<{mesh: THREE.SkinnedMesh, bones: object, morphTargets: object}>}
     */
    loadModel(modelPath, onProgress) {
        console.log(`[ModelLoader] loadModel() called — path: ${modelPath}`);

        return new Promise((resolve, reject) => {
            const startTime = performance.now();

            const handleProgress = (event) => {
                if (event.lengthComputable) {
                    const pct = Math.round((event.loaded / event.total) * 100);
                    console.log(`[ModelLoader] Loading progress: ${pct}%`);
                    if (onProgress) onProgress(pct);
                } else {
                    console.log('[ModelLoader] Loading progress: indeterminate (total unknown)');
                }
            };

            this.loader.load(
                modelPath,
                // onLoad
                (mesh) => {
                    const elapsed = ((performance.now() - startTime) / 1000).toFixed(2);
                    console.log(`[ModelLoader] Model loaded successfully in ${elapsed}s`);
                    console.log(`[ModelLoader] Mesh geometry vertices: ${mesh.geometry.attributes.position?.count ?? 'N/A'}`);

                    // Extract bones from the skeleton
                    const bones = {};
                    if (mesh.skeleton && mesh.skeleton.bones) {
                        mesh.skeleton.bones.forEach((bone) => {
                            bones[bone.name] = bone;
                        });
                        console.log(`[ModelLoader] Skeleton bones count: ${mesh.skeleton.bones.length}`);
                        console.log(`[ModelLoader] Bone names: ${Object.keys(bones).join(', ')}`);
                    } else {
                        console.warn('[ModelLoader] No skeleton found on loaded mesh');
                    }

                    // Extract morph target names — traverse ALL child meshes
                    const morphTargets = {};
                    mesh.traverse((child) => {
                        if (child.isSkinnedMesh && child.morphTargetDictionary) {
                            Object.entries(child.morphTargetDictionary).forEach(([name, index]) => {
                                // Store with reference to which mesh owns this morph
                                if (!morphTargets[name]) {
                                    morphTargets[name] = { index, mesh: child };
                                }
                            });
                        }
                    });
                    console.log(`[ModelLoader] Morph target count (all sub-meshes): ${Object.keys(morphTargets).length}`);
                    console.log(`[ModelLoader] Morph target names: ${Object.keys(morphTargets).join(', ')}`);

                    resolve({ mesh, bones, morphTargets });
                },
                // onProgress
                handleProgress,
                // onError
                (error) => {
                    console.error(`[ModelLoader] Failed to load model: ${modelPath}`, error);
                    reject(new Error(`Failed to load model ${modelPath}: ${error.message || error}`));
                }
            );
        });
    }

    /**
     * Load a VMD animation file (raw data, for MMDAnimationHelper).
     */
    loadVmd(vmdPath, mesh) {
        console.log(`[ModelLoader] loadVmd() — path: ${vmdPath}`);
        return new Promise((resolve, reject) => {
            this.loader.loadVMD(vmdPath, (vmd) => {
                console.log(`[ModelLoader] VMD loaded: ${vmdPath}`);
                resolve(vmd);
            }, null, (err) => {
                console.error(`[ModelLoader] VMD load failed: ${vmdPath}`, err);
                reject(err);
            });
        });
    }

    /**
     * Load VMD animation and return an AnimationClip (properly adapted to mesh bones).
     * Uses MMDLoader.loadAnimation which returns a proper THREE.AnimationClip.
     */
    loadAnimationClip(vmdPath, mesh) {
        console.log(`[ModelLoader] loadAnimationClip: ${vmdPath}`);
        return new Promise((resolve, reject) => {
            this.loader.loadAnimation(vmdPath, mesh, (clip) => {
                console.log(`[ModelLoader] AnimationClip loaded: ${clip.name}, duration=${clip.duration.toFixed(1)}s`);
                resolve(clip);
            }, null, (err) => {
                console.error(`[ModelLoader] loadAnimationClip failed:`, err);
                reject(err);
            });
        });
    }

    /**
     * Load a Hunyuan / Mixamo FBX file for retargeting.
     */
    loadFbx(fbxPath, onProgress) {
        console.log(`[ModelLoader] loadFbx() — path: ${fbxPath}`);
        return new Promise((resolve, reject) => {
            const loader = new FBXLoader();
            loader.load(
                fbxPath,
                (object) => {
                    console.log(`[ModelLoader] FBX loaded: animations=${object.animations.length}`);
                    resolve(object);
                },
                onProgress,
                (error) => {
                    console.error(`[ModelLoader] FBX load failed: ${fbxPath}`, error);
                    reject(error);
                }
            );
        });
    }

    /**
     * Load VMD animation and create playing AnimationMixer.
     * Uses the correct MMDLoader.loadAnimation API for proper AnimationClip.
     */
    async loadAnimation(vmdPath, mesh) {
        console.log(`[ModelLoader] loadAnimation: ${vmdPath}`);
        const clip = await this.loadAnimationClip(vmdPath, mesh);
        const mixer = new THREE.AnimationMixer(mesh);
        const action = mixer.clipAction(clip);
        action.setLoop(THREE.LoopRepeat);
        action.play();
        console.log(`[ModelLoader] VMD animation playing: ${clip.name}`);
        return mixer;
    }

    /**
     * Fully dispose a loaded mesh: geometry, materials, and textures.
     *
     * @param {THREE.SkinnedMesh} mesh
     */
    unloadModel(mesh) {
        if (!mesh) {
            console.warn('[ModelLoader] unloadModel() called with null/undefined mesh — skipping');
            return;
        }

        console.log('[ModelLoader] unloadModel() — starting disposal');

        // Dispose geometry
        if (mesh.geometry) {
            mesh.geometry.dispose();
            console.log('[ModelLoader] Geometry disposed');
        }

        // Dispose materials and their textures
        const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
        materials.forEach((mat, i) => {
            if (!mat) return;

            // Walk known texture properties
            const textureProps = [
                'map', 'lightMap', 'bumpMap', 'normalMap', 'specularMap',
                'envMap', 'alphaMap', 'aoMap', 'displacementMap',
                'emissiveMap', 'metalnessMap', 'roughnessMap',
                'gradientMap', 'sphereMap',
            ];

            textureProps.forEach((prop) => {
                const tex = mat[prop];
                if (tex && tex.isTexture) {
                    tex.dispose();
                }
            });

            mat.dispose();
        });
        console.log(`[ModelLoader] ${materials.length} material(s) disposed`);

        // Clear parent reference
        if (mesh.parent) {
            mesh.parent.remove(mesh);
        }

        console.log('[ModelLoader] Model unloaded and fully disposed');
    }
}
