/**
 * MotionEngine — stable idle + expressive upper-body gestures.
 *
 * Idle: breathing, blink, slow head glance.
 * Gestures: nod, bow, wave, think, happy, shy, point, stretch, shrug.
 *
 * All gestures are procedural and additive over the rest pose, so they work
 * on both PMX characters without depending on a specific VMD rig.
 */
const BONE_CANDIDATES = {
    head:       ['首', '頭', 'head', 'Head', 'neck'],
    neck:       ['neck', 'neck1', 'Neck'],
    upperBody:  ['上半身2', '上半身', 'spine', 'chest', 'Spine', 'Chest'],
    upperBody2: ['上半身3', '上半身2', 'chest2', 'Chest2'],
    lShoulder:  ['左肩', '左肩P', 'left_shoulder', 'shoulder_L'],
    lArm:       ['左腕', 'left_arm', 'arm_L'],
    lForearm:   ['左ひじ', '左腕捩', 'left_forearm'],
    lHand:      ['左手首', 'left_hand', 'hand_L', 'wrist_L'],
    rShoulder:  ['右肩', '右肩P', 'right_shoulder', 'shoulder_R'],
    rArm:       ['右腕', 'right_arm', 'arm_R'],
    rForearm:   ['右ひじ', '右腕捩', 'right_forearm'],
    rHand:      ['右手首', 'right_hand', 'hand_R', 'wrist_R'],
};

const BONE_KEYS = Object.keys(BONE_CANDIDATES);

const EYE_CLOSE_NAMES = ['目閉じ', 'eye_close', 'まばたき', '閉眼', 'wink', '目'];

const clamp01 = (value) => Math.max(0, Math.min(1, value));
const envelope = (t) => Math.sin(Math.PI * clamp01(t));
const smooth = (t) => {
    const x = clamp01(t);
    return x * x * (3 - 2 * x);
};

function addOffset(offset, bone, x = 0, y = 0, z = 0) {
    const target = offset[bone] || (offset[bone] = { x: 0, y: 0, z: 0 });
    target.x += x;
    target.y += y;
    target.z += z;
}

export class MotionEngine {
    constructor(mesh, bones, morphTargets, expressionEngine) {
        this.mesh = mesh;
        this.bones = bones || {};
        this.morphTargets = morphTargets || {};
        this.expressionEngine = expressionEngine || null;
        this.enabled = true;

        BONE_KEYS.forEach((key) => {
            this[key] = this._findBone(BONE_CANDIDATES[key], key);
        });

        this._applyArmDownPose();
        this._saveBase();

        // Blink
        this.blinkTimer = 0;
        this.blinkInterval = this._randBlink();
        this.blinkElapsed = 0;
        this.blinkDuration = 0.15;
        this.isBlinking = false;
        this.eyeCloseInfo = this._resolveEyeClose();

        // Idle head glance
        this.idleTime = 0;
        this.glanceTimer = 0;
        this.glanceInterval = 3 + Math.random() * 4;
        this.glanceAngle = 0;
        this.glanceCurrent = 0;
        this.glanceSpeed = 0.7;

        // Ambient conversation idle: weight shifts, hand fidgets, looking around.
        this.ambientKinds = ['weightShift', 'lookAround', 'handFidget', 'sleeve'];
        this.ambientKind = '';
        this.ambientElapsed = 0;
        this.ambientDuration = 0;
        this.ambientTimer = 3 + Math.random() * 3;

        // Gesture timeline
        this.gesture = null;

        // Speaking gestures
        this.speaking = false;
        this.speakTime = 0;

        console.log('[MotionEngine] Ready — idle breathing + 9 gestures');
    }

    _findBone(candidates, label) {
        for (const name of candidates) {
            if (this.bones[name]) {
                if (label !== 'head') {
                    console.log(`[MotionEngine] ${label} → "${name}"`);
                }
                return this.bones[name];
            }
        }
        return null;
    }

    _applyArmDownPose() {
        if (this.lShoulder) this.lShoulder.rotation.z = -0.3;
        if (this.rShoulder) this.rShoulder.rotation.z = 0.3;
        if (this.lArm) this.lArm.rotation.z = -0.2;
        if (this.rArm) this.rArm.rotation.z = 0.2;
        if (this.lForearm) this.lForearm.rotation.x = 0.15;
        if (this.rForearm) this.rForearm.rotation.x = 0.15;
    }

    _saveBase() {
        this._base = {};
        for (const key of BONE_KEYS) {
            const bone = this[key];
            if (bone) {
                this._base[key] = {
                    x: bone.rotation.x,
                    y: bone.rotation.y,
                    z: bone.rotation.z,
                };
            }
        }
    }

    _resolveEyeClose() {
        for (const name of EYE_CLOSE_NAMES) {
            const entry = this.morphTargets[name];
            if (entry && entry.mesh) {
                return { index: entry.index, targetMesh: entry.mesh };
            }
        }
        return null;
    }

    _randBlink() {
        return 2.5 + Math.random() * 3.5;
    }

    // ── Public API ───────────────────────────────────────────────

    /** Play a named gesture. Returns true when the gesture exists. */
    playGesture(name) {
        const definition = this._gestureDefinitions()[name];
        if (!definition) return false;
        this.gesture = {
            name,
            elapsed: 0,
            duration: definition.duration,
        };
        console.log(`[MotionEngine] Gesture: ${name}`);
        return true;
    }

    /** Stop the active gesture and blend back to idle. */
    stopGesture() {
        this.gesture = null;
    }

    getGesture() {
        return this.gesture ? this.gesture.name : null;
    }

    setSpeaking(value) {
        this.speaking = Boolean(value);
        if (!this.speaking) {
            this.speakTime = 0;
        }
    }

    /** Compatibility with the old API. */
    trigger(type) {
        return this.playGesture(type);
    }

    endSpeaking() {
        this.setSpeaking(false);
    }

    /** Blink-only update, used while a VMD idle clip owns the skeleton. */
    updateFacial(delta) {
        this._blink(delta);
    }

    // ── Update ───────────────────────────────────────────────────

    update(delta) {
        if (!this.enabled) return;

        this.idleTime += delta;
        this._blink(delta);
        this._updateGlance(delta);
        this._updateAmbient(delta);
        this._updateGesture(delta);

        if (this.speaking) {
            this.speakTime += delta;
        }

        const offset = this._idleOffsets();
        const progress = this._currentGestureProgress();
        if (progress) {
            this._applyGestureOffsets(offset, progress);
        }
        this._applySpeakingOffsets(offset);
        this._applyOffsets(offset);
    }

    _updateGesture(delta) {
        if (!this.gesture) return;
        this.gesture.elapsed += delta;
        if (this.gesture.elapsed >= this.gesture.duration) {
            this.gesture = null;
        }
    }

    _currentGestureProgress() {
        if (!this.gesture) return null;
        const t = clamp01(this.gesture.elapsed / this.gesture.duration);
        return { t, env: envelope(t) };
    }

    _idleOffsets() {
        const offset = {};
        const breathe = Math.sin(this.idleTime * 1.35) * 0.009;
        addOffset(offset, 'upperBody', breathe, 0, 0);
        addOffset(offset, 'upperBody2', breathe * 0.6, 0, 0);

        // Continuous conversational sway: never freeze into a mannequin.
        const sway = Math.sin(this.idleTime * 0.55) * 0.028;
        const armSway = Math.sin(this.idleTime * 0.82 + 1.1) * 0.02;
        addOffset(offset, 'upperBody', 0, 0, sway);
        addOffset(offset, 'upperBody2', 0, 0, -sway * 0.35);
        addOffset(offset, 'head', 0.008 * Math.sin(this.idleTime * 1.1), -sway * 0.5, -sway * 0.25);
        addOffset(offset, 'lArm', 0, 0, armSway);
        addOffset(offset, 'rArm', 0, 0, -armSway);
        addOffset(offset, 'lForearm', 0, 0, -armSway * 0.4);
        addOffset(offset, 'rForearm', 0, 0, armSway * 0.4);

        if (this.ambientKind) {
            const env = envelope(this.ambientElapsed / this.ambientDuration);
            if (this.ambientKind === 'weightShift') {
                addOffset(offset, 'upperBody', 0, 0, 0.075 * env);
                addOffset(offset, 'upperBody2', 0, 0, -0.028 * env);
                addOffset(offset, 'head', 0.015 * env, 0, -0.05 * env);
                addOffset(offset, 'lArm', 0, 0, 0.04 * env);
                addOffset(offset, 'rArm', 0, 0, -0.03 * env);
            } else if (this.ambientKind === 'lookAround') {
                const side = Math.sin(this.ambientElapsed * 1.7);
                addOffset(offset, 'head', 0.02 * env, 0.24 * side * env, 0.04 * env);
                addOffset(offset, 'upperBody', 0, 0.035 * side * env, 0);
            } else if (this.ambientKind === 'handFidget') {
                addOffset(offset, 'rArm', 0.04 * env, 0, -0.1 * env);
                addOffset(offset, 'rForearm', 0, 0, -0.28 * env);
                addOffset(offset, 'rHand', 0.08 * env, 0, 0.2 * env);
                addOffset(offset, 'lForearm', 0, 0, 0.1 * env);
                addOffset(offset, 'head', 0.05 * env, 0, -0.035 * env);
            } else if (this.ambientKind === 'sleeve') {
                addOffset(offset, 'lShoulder', 0, 0, 0.05 * env);
                addOffset(offset, 'rShoulder', 0, 0, -0.05 * env);
                addOffset(offset, 'lForearm', 0, 0, 0.2 * env);
                addOffset(offset, 'rForearm', 0, 0, -0.2 * env);
                addOffset(offset, 'lHand', 0.14 * env, 0, 0.1 * env);
                addOffset(offset, 'rHand', -0.14 * env, 0, -0.1 * env);
            }
        }

        addOffset(offset, 'head', 0, this.glanceCurrent, 0);
        return offset;
    }

    _updateAmbient(delta) {
        if (this.gesture) return;
        if (this.ambientKind) {
            this.ambientElapsed += delta;
            if (this.ambientElapsed >= this.ambientDuration) {
                this.ambientKind = '';
                this.ambientElapsed = 0;
                this.ambientTimer = 5 + Math.random() * 5;
            }
            return;
        }
        this.ambientTimer -= delta;
        if (this.ambientTimer > 0) return;
        this.ambientKind = this.ambientKinds[Math.floor(Math.random() * this.ambientKinds.length)];
        this.ambientElapsed = 0;
        this.ambientDuration = 2.4 + Math.random() * 1.8;
    }

    _applyGestureOffsets(offset, progress) {
        const definition = this._gestureDefinitions()[this.gesture?.name];
        if (definition) {
            definition.apply(offset, progress.t, progress.env);
        }
    }

    _applySpeakingOffsets(offset) {
        if (!this.speaking) return;
        const nod = Math.sin(this.speakTime * 3.1) * 0.025;
        const sway = Math.sin(this.speakTime * 1.7) * 0.018;
        addOffset(offset, 'head', nod, sway, 0);
        addOffset(offset, 'upperBody', nod * 0.35, 0, 0);
        addOffset(offset, 'rForearm', 0, 0, -Math.abs(nod) * 1.2);
    }

    _applyOffsets(offset) {
        for (const key of BONE_KEYS) {
            const bone = this[key];
            const base = this._base[key];
            if (!bone || !base) continue;
            // Avoid applying the same fallback bone twice.
            if (key === 'upperBody2' && this.upperBody2 && this.upperBody2 === this.upperBody) continue;
            if (key === 'neck' && this.neck && this.neck === this.head) continue;
            const extra = offset[key] || { x: 0, y: 0, z: 0 };
            bone.rotation.set(base.x + extra.x, base.y + extra.y, base.z + extra.z);
        }
    }

    // ── Gesture definitions ─────────────────────────────────────

    _gestureDefinitions() {
        return {
            nod: {
                duration: 0.85,
                apply: (offset, t, env) => {
                    const beat = Math.sin(t * Math.PI * 4);
                    addOffset(offset, 'head', beat * 0.16 * env, 0, 0);
                    addOffset(offset, 'neck', beat * 0.05 * env, 0, 0);
                    addOffset(offset, 'upperBody', beat * 0.02 * env, 0, 0);
                },
            },
            bow: {
                duration: 1.8,
                apply: (offset, t, env) => {
                    const bend = t < 0.72
                        ? smooth(Math.min(1, t * 2.2))
                        : smooth((1 - t) / 0.28);
                    addOffset(offset, 'upperBody', 0.3 * bend, 0, 0);
                    addOffset(offset, 'upperBody2', 0.12 * bend, 0, 0);
                    addOffset(offset, 'head', 0.16 * bend, 0, 0);
                    addOffset(offset, 'lArm', 0, 0, 0.08 * env);
                    addOffset(offset, 'rArm', 0, 0, -0.08 * env);
                },
            },
            wave: {
                duration: 2.4,
                apply: (offset, t, env) => {
                    const raise = smooth(Math.min(1, t * 2.5));
                    addOffset(offset, 'rShoulder', 0, 0, -0.25 * raise);
                    addOffset(offset, 'rArm', 0.2 * raise, 1.2 * raise, -0.8 * raise);
                    addOffset(offset, 'rForearm', 0, 0, -1.1 * raise - Math.sin(t * Math.PI * 7) * 0.16 * env);
                    addOffset(offset, 'rHand', 0, 0, Math.sin(t * Math.PI * 7 + 0.4) * 0.24 * env);
                    addOffset(offset, 'head', -0.03 * env, -0.08 * env, 0.04 * env);
                },
            },
            think: {
                duration: 1.8,
                apply: (offset, t, env) => {
                    // Just a quiet downward head dip; no arm movement.
                    addOffset(offset, 'head', 0.22 * env, 0, 0);
                    addOffset(offset, 'neck', 0.06 * env, 0, 0);
                },
            },
            happy: {
                duration: 1.7,
                apply: (offset, t, env) => {
                    const sway = Math.sin(t * Math.PI * 4);
                    addOffset(offset, 'upperBody', 0, 0, sway * 0.035 * env);
                    addOffset(offset, 'upperBody2', 0, 0, sway * 0.018 * env);
                    addOffset(offset, 'head', 0.05 * env, 0, -sway * 0.06 * env);
                    addOffset(offset, 'lArm', 0, 0, -0.1 * env);
                    addOffset(offset, 'rArm', 0, 0, 0.1 * env);
                    addOffset(offset, 'lForearm', 0, 0, 0.24 * env);
                    addOffset(offset, 'rForearm', 0, 0, -0.24 * env);
                },
            },
            shy: {
                duration: 2.0,
                apply: (offset, t, env) => {
                    const pose = smooth(Math.min(1, t * 2.0));
                    addOffset(offset, 'head', 0.16 * pose, 0.12 * pose, -0.03 * pose);
                    addOffset(offset, 'upperBody', 0.055 * pose, 0, 0);
                    addOffset(offset, 'lForearm', 0, 0, 0.48 * pose);
                    addOffset(offset, 'rForearm', 0, 0, -0.48 * pose);
                    addOffset(offset, 'lArm', 0, 0, 0.08 * pose);
                    addOffset(offset, 'rArm', 0, 0, -0.08 * pose);
                },
            },
            point: {
                duration: 1.7,
                apply: (offset, t, env) => {
                    const pose = smooth(Math.min(1, t * 2.4));
                    addOffset(offset, 'rShoulder', 0, 0, -0.3 * pose);
                    addOffset(offset, 'rArm', -0.55 * pose, 0, -0.45 * pose);
                    addOffset(offset, 'rForearm', 0, 0, -0.22 * pose);
                    addOffset(offset, 'head', -0.03 * env, -0.13 * env, 0);
                    addOffset(offset, 'upperBody', 0, -0.04 * env, 0);
                },
            },
            stretch: {
                duration: 2.2,
                apply: (offset, t, env) => {
                    const pose = smooth(Math.min(1, t * 2.2));
                    addOffset(offset, 'lShoulder', 0, 0, 0.2 * pose);
                    addOffset(offset, 'rShoulder', 0, 0, -0.2 * pose);
                    addOffset(offset, 'lArm', -0.2 * pose, -1.2 * pose, 1.8 * pose);
                    addOffset(offset, 'rArm', -0.2 * pose, 1.2 * pose, -1.8 * pose);
                    addOffset(offset, 'lForearm', 0, 0, 0.3 * pose);
                    addOffset(offset, 'rForearm', 0, 0, -0.3 * pose);
                    addOffset(offset, 'head', -0.08 * pose, 0, 0);
                    addOffset(offset, 'upperBody', -0.03 * pose, 0, 0);
                },
            },
            shrug: {
                duration: 2.0,
                apply: (offset, t, env) => {
                    const pose = smooth(Math.min(1, t * 3.0));
                    const sway = Math.sin(t * Math.PI * 2) * env;
                    addOffset(offset, 'lShoulder', 0.2 * pose, -0.1 * pose, 0.42 * pose);
                    addOffset(offset, 'rShoulder', 0.2 * pose, 0.1 * pose, -0.42 * pose);
                    addOffset(offset, 'lArm', 0.2 * pose, -0.3 * pose, 0.75 * pose);
                    addOffset(offset, 'rArm', 0.2 * pose, 0.3 * pose, -0.75 * pose);
                    addOffset(offset, 'lForearm', -0.1 * pose, -0.2 * pose, 0.9 * pose);
                    addOffset(offset, 'rForearm', -0.1 * pose, 0.2 * pose, -0.9 * pose);
                    addOffset(offset, 'lHand', 0, 0, 0.18 * pose);
                    addOffset(offset, 'rHand', 0, 0, -0.18 * pose);
                    addOffset(offset, 'head', 0.03 * pose, 0.08 * sway, 0.06 * pose);
                    addOffset(offset, 'upperBody', 0, 0, sway * 0.02);
                },
            },
        };
    }

    // ── Blink / glance ───────────────────────────────────────────

    _blink(delta) {
        if (!this.eyeCloseInfo) return;
        const { index, targetMesh } = this.eyeCloseInfo;
        const infl = targetMesh.morphTargetInfluences;
        if (!infl || index >= infl.length) return;
        if (this.isBlinking) {
            this.blinkElapsed += delta;
            const t = this.blinkElapsed / this.blinkDuration;
            if (t >= 1) {
                infl[index] = 0;
                this.isBlinking = false;
                this.blinkElapsed = 0;
                this.blinkInterval = this._randBlink();
                this.blinkTimer = 0;
            } else {
                infl[index] = 1 - Math.pow(2 * t - 1, 2);
            }
        } else {
            // Idle VMD clips can contain their own blink/morph tracks. When
            // we are not blinking, force the eye-close morph back to zero so
            // a track value or a half-finished blink cannot leave the eyes shut.
            infl[index] = 0;
            this.blinkTimer += delta;
            if (this.blinkTimer >= this.blinkInterval) {
                this.isBlinking = true;
                this.blinkElapsed = 0;
            }
        }
    }

    _updateGlance(delta) {
        this.glanceTimer += delta;
        if (this.glanceTimer > this.glanceInterval) {
            this.glanceTimer = 0;
            this.glanceInterval = 2 + Math.random() * 3;
            this.glanceAngle = (Math.random() - 0.5) * 0.36;
        }
        this.glanceCurrent += (this.glanceAngle - this.glanceCurrent) * Math.min(delta * this.glanceSpeed, 0.5);
    }

    dispose() {
        this.mesh = null;
        this.bones = null;
        this.morphTargets = null;
        this.expressionEngine = null;
        for (const key of BONE_KEYS) this[key] = null;
        this.eyeCloseInfo = null;
        this._base = null;
        this.gesture = null;
    }
}
