/**
 * LipSync — audio-driven mouth morph animation.
 *
 * Morph target format: {name: {index: number, mesh: SkinnedMesh}}
 */
const MOUTH_MORPH_CANDIDATES = {
    mouthOpen:  ['あ', 'a', 'mouth_open', '開口', 'mouthOpen', 'open', '口開'],
    mouthNarrow:['い', 'i', 'mouth_narrow', 'mouthNarrow', 'narrow', 'う', 'u'],
    mouthWide:  ['え', 'e', 'mouth_wide', 'mouthWide', 'wide', 'お', 'o', 'にっこり'],
};
const LOW_BAND_END = 4;
const MID_BAND_END = 12;

export class LipSync {
    constructor(mesh, morphTargets) {
        this.mesh = mesh;
        this.morphTargets = morphTargets || {};
        this.audioContext = null;
        this.analyser = null;
        this.source = null;
        this.mediaStream = null;
        this.frequencyData = null;

        // Resolved per-shape: {index, targetMesh}
        this.mouthOpen = null;
        this.mouthNarrow = null;
        this.mouthWide = null;
        this._resolveMorphs();

        this.currentOpen = 0;
        this.currentNarrow = 0;
        this.currentWide = 0;
        this.smoothSpeed = 8.0;
        this.volumeThreshold = 5.0;
        this.isActive = false;

        console.log(`[LipSync] Ready — open:${!!this.mouthOpen} narrow:${!!this.mouthNarrow} wide:${!!this.mouthWide}`);
    }

    _resolveMorphs() {
        const keys = Object.keys(this.morphTargets);
        console.log(`[LipSync] Available morphs (${keys.length}): ${keys.slice(0, 30).join(', ')}...`);

        for (const [component, candidates] of Object.entries(MOUTH_MORPH_CANDIDATES)) {
            let found = null;
            for (const name of candidates) {
                const entry = this.morphTargets[name];
                if (entry && entry.mesh) {
                    found = { index: entry.index, targetMesh: entry.mesh };
                    console.log(`[LipSync] "${component}" → "${name}" idx=${entry.index} on mesh "${entry.mesh.name || 'unnamed'}"`);
                    break;
                }
            }
            if (!found) {
                console.warn(`[LipSync] "${component}" NOT FOUND. Tried: ${candidates.join(', ')}`);
            }
            if (component === 'mouthOpen') this.mouthOpen = found;
            else if (component === 'mouthNarrow') this.mouthNarrow = found;
            else if (component === 'mouthWide') this.mouthWide = found;
        }
    }

    connect(audioTrack) {
        if (!audioTrack) { console.error('[LipSync] null track'); return; }
        if (this.isActive) this.disconnect();

        try {
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            this.mediaStream = new MediaStream([audioTrack]);
            this.source = this.audioContext.createMediaStreamSource(this.mediaStream);
            this.analyser = this.audioContext.createAnalyser();
            this.analyser.fftSize = 512;
            this.analyser.smoothingTimeConstant = 0.4;
            this.frequencyData = new Uint8Array(this.analyser.frequencyBinCount);
            this.source.connect(this.analyser);

            if (this.audioContext.state === 'suspended') {
                console.log('[LipSync] AudioContext is suspended; waiting for resume()');
            }
            this.isActive = true;
            console.log('[LipSync] Connected — ACTIVE');
        } catch (err) {
            console.error('[LipSync] connect failed:', err);
            this.disconnect();
        }
    }

    /** Connect from HTML <audio> element (HTTP audio playback). */
    connectFromElement(audioElement) {
        if (!audioElement) { console.error('[LipSync] null element'); return; }
        if (this.isActive) this.disconnect();

        try {
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            this.source = this.audioContext.createMediaElementSource(audioElement);
            this.analyser = this.audioContext.createAnalyser();
            this.analyser.fftSize = 512;
            this.analyser.smoothingTimeConstant = 0.4;
            this.frequencyData = new Uint8Array(this.analyser.frequencyBinCount);
            this.source.connect(this.analyser);
            this.analyser.connect(this.audioContext.destination);
            this.isActive = true;
            console.log('[LipSync] Connected from <audio> element');
        } catch (err) {
            console.error('[LipSync] connectFromElement failed:', err);
            this.disconnect();
        }
    }

    async resume() {
        if (!this.audioContext || this.audioContext.state !== 'suspended') return;

        try {
            await this.audioContext.resume();
            console.log('[LipSync] AudioContext resumed');
        } catch (err) {
            console.error('[LipSync] resume failed:', err);
        }
    }

    update(delta) {
        if (!this.isActive || !this.analyser || !this.frequencyData) return;

        this.analyser.getByteFrequencyData(this.frequencyData);
        const binCount = this.frequencyData.length;
        const lowEnd = Math.min(LOW_BAND_END, binCount);
        const midEnd = Math.min(MID_BAND_END, binCount);

        let lowSum = 0, midSum = 0, highSum = 0;
        let lowN = 0, midN = 0, highN = 0;
        for (let i = 0; i < lowEnd; i++) { lowSum += this.frequencyData[i]; lowN++; }
        for (let i = lowEnd; i < midEnd; i++) { midSum += this.frequencyData[i]; midN++; }
        for (let i = midEnd; i < binCount; i++) { highSum += this.frequencyData[i]; highN++; }

        const lowE = lowN > 0 ? lowSum / lowN : 0;
        const midE = midN > 0 ? midSum / midN : 0;
        const highE = highN > 0 ? highSum / highN : 0;
        const totalE = (lowE + midE + highE) / 3;

        let tOpen, tNarrow, tWide;
        if (totalE < this.volumeThreshold) {
            tOpen = 0; tNarrow = 0; tWide = 0;
        } else {
            tOpen = Math.min(lowE / 255, 1) * 0.8;
            tNarrow = Math.min(midE / 255, 1) * 0.6;
            tWide = Math.min(highE / 255, 1) * 0.5;
        }

        const t = Math.min(this.smoothSpeed * delta, 1);
        this.currentOpen += (tOpen - this.currentOpen) * t;
        this.currentNarrow += (tNarrow - this.currentNarrow) * t;
        this.currentWide += (tWide - this.currentWide) * t;

        this._apply(this.mouthOpen, this.currentOpen);
        this._apply(this.mouthNarrow, this.currentNarrow);
        this._apply(this.mouthWide, this.currentWide);
    }

    _apply(target, weight) {
        if (!target || !target.targetMesh) {
            if (!this._applyWarned) { console.warn('[LipSync] _apply: target or mesh is null'); this._applyWarned = true; }
            return;
        }
        const infl = target.targetMesh.morphTargetInfluences;
        if (!infl || target.index >= infl.length) {
            if (!this._applyWarned) { console.warn(`[LipSync] _apply: index ${target.index} out of range (max ${infl ? infl.length : 'null'})`); this._applyWarned = true; }
            return;
        }
        infl[target.index] = weight;
    }

    /** Apply server-computed mouth weights directly (bypasses audio analysis). */
    applyServerWeights(mouthOpen, mouthWide) {
        this.currentOpen += (mouthOpen - this.currentOpen) * 0.6;
        this.currentNarrow += (mouthWide * 0.5 - this.currentNarrow) * 0.6;
        this.currentWide += (mouthWide - this.currentWide) * 0.6;

        // Log every 30th frame
        this._applyCount = (this._applyCount || 0) + 1;
        if (this._applyCount % 30 === 0) {
            console.log(`[LipSync] frame#${this._applyCount}: open=${this.currentOpen.toFixed(3)} narrow=${this.currentNarrow.toFixed(3)} wide=${this.currentWide.toFixed(3)}`);
        }

        this._apply(this.mouthOpen, this.currentOpen);
        this._apply(this.mouthNarrow, this.currentNarrow);
        this._apply(this.mouthWide, this.currentWide);
    }

    disconnect() {
        if (this.analyser) { this.analyser.disconnect(); this.analyser = null; }
        if (this.source) { this.source.disconnect(); this.source = null; }
        if (this.mediaStream) { this.mediaStream = null; }
        if (this.audioContext) { this.audioContext.close().catch(() => {}); this.audioContext = null; }
        this._apply(this.mouthOpen, 0);
        this._apply(this.mouthNarrow, 0);
        this._apply(this.mouthWide, 0);
        this.currentOpen = 0; this.currentNarrow = 0; this.currentWide = 0;
        this.frequencyData = null;
        this.isActive = false;
        console.log('[LipSync] Disconnected');
    }

    isConnected() { return this.isActive; }
}
