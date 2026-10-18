/**
 * ExpressionEngine — rich emotion system driving morph targets.
 * Supported: neutral, happy, sad, surprised, shy, thinking, excited
 */
const MORPH_NAMES = {
    // "笑い" / "笑い2" are eye-closing smile variants on this PMX. Use the
    // mouth-corner morph so a smile does not squint the eyes shut.
    smile:    ['口角上げ', 'にっこり', 'smile', '开心', '笑', 'えがお', '喜び'],
    frown:    ['困る', 'なやみ', 'frown', '悲伤', '怒', '悲しい', '怒り'],
    eyeClose: ['目閉じ', 'eye_close', 'まばたき', '閉眼', 'ウィンク', 'wink', '目'],
    browUp:   ['眉上', 'brow_up', 'アップ', 'びっくり'],
    browDown: ['困る', 'brow_down', '怒り', '真面目', 'キリッ', 'ｷﾘｯ'],
    mouthOpen:['あ', 'a', 'mouth_open', '開口', '口開', 'お'],
    eyeWide:  ['びっくり', 'eye_wide', '驚かす', '驚き'],
    cheekUp:  ['cheek_up'],
};

const EMOTION_PRESETS = {
    neutral:    { smile:0, frown:0, browUp:0, browDown:0, mouthOpen:0.05, eyeWide:0, cheekUp:0 },
    happy:      { smile:1.0, frown:0, browUp:0.3, browDown:0, mouthOpen:0.25, eyeWide:0.1, cheekUp:0.8 },
    sad:        { smile:0, frown:0.7, browUp:0.6, browDown:0.2, mouthOpen:0, eyeWide:0, cheekUp:0 },
    surprised:  { smile:0, frown:0, browUp:0.9, browDown:0, mouthOpen:0.5, eyeWide:0.8, cheekUp:0 },
    shy:        { smile:0.5, frown:0, browUp:0.2, browDown:0.1, mouthOpen:0.1, eyeWide:0, cheekUp:0.5 },
    thinking:   { smile:0.1, frown:0.2, browUp:0.1, browDown:0.5, mouthOpen:0, eyeWide:0, cheekUp:0 },
    excited:    { smile:1.0, frown:0, browUp:0.5, browDown:0, mouthOpen:0.4, eyeWide:0.4, cheekUp:1.0 },
};

export class ExpressionEngine {
    constructor(mesh, morphTargets) {
        this.mesh = mesh;
        this.morphTargets = morphTargets || {};
        this.currentEmotion = 'neutral';
        this.targetWeights = { ...EMOTION_PRESETS.neutral };
        this.currentWeights = { ...EMOTION_PRESETS.neutral };
        this.lerpSpeed = 3.0;

        // component → {index, targetMesh}
        this.componentMap = {};
        this._resolve();

        // Micro-expression timer
        this.microTimer = 0;
        this.microType = null;
        this.microWeights = {};

        console.log(`[ExpressionEngine] Ready — ${Object.keys(this.componentMap).length} components`);
    }

    _resolve() {
        for (const [comp, candidates] of Object.entries(MORPH_NAMES)) {
            let found = null;
            for (const name of candidates) {
                const entry = this.morphTargets[name];
                if (entry && entry.mesh) { found = { index: entry.index, targetMesh: entry.mesh }; break; }
            }
            this.componentMap[comp] = found;
            if (!found && comp !== 'cheekUp') console.warn(`[ExpressionEngine] "${comp}" not found`);
        }
    }

    setEmotion(emotion) {
        const preset = EMOTION_PRESETS[emotion];
        if (!preset) { this._setTarget('neutral'); return; }
        this._setTarget(emotion);
    }

    _setTarget(emotion) {
        if (this.currentEmotion === emotion) return;
        this.currentEmotion = emotion;
        this.targetWeights = { ...EMOTION_PRESETS[emotion] };
    }

    update(delta) {
        const t = Math.min(this.lerpSpeed * delta, 1);
        // Random micro-expressions when neutral
        this.microTimer += delta;
        if (this.currentEmotion === 'neutral' && this.microTimer > 4 + Math.random() * 6) {
            this.microTimer = 0;
            const types = ['smile', 'browUp', 'browDown'];
            this.microType = types[Math.floor(Math.random() * types.length)];
            this.microWeights = { ...this.targetWeights };
            this.microWeights[this.microType] = 0.15 + Math.random() * 0.15;
        }
        // Fade micro-expression
        if (this.microType && this.microTimer > 1.5) {
            this.microType = null;
            this.microWeights = {};
        }

        const weights = this.microType ? this.microWeights : this.targetWeights;
        for (const [comp, targetW] of Object.entries(weights)) {
            const cur = this.currentWeights[comp] ?? 0;
            const next = cur + (targetW - cur) * t;
            this.currentWeights[comp] = next;
            const info = this.componentMap[comp];
            if (info && info.targetMesh) {
                const infl = info.targetMesh.morphTargetInfluences;
                if (infl && info.index < infl.length) infl[info.index] = next;
            }
        }
    }

    getMorphInfo(component) { return this.componentMap[component] || null; }
}
