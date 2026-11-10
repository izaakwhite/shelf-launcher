#!/usr/bin/env python3
"""Generates Shelf's original UI sounds and Wave ambient loop into app/src/main/res/raw/.
Everything is synthesized here; no recorded or third-party audio is used."""
import math, random, struct, wave, os

RAW = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')

def write(name, samples, sr):
    w = wave.open(os.path.join(RAW, name), 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
    w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32000)) for s in samples)); w.close()

def click(dur, decay, tone_hz, tone_amp, noise_amp, lp, vol, seed):
    """A short, soft mechanical click: a filtered noise burst plus a tiny damped body tone."""
    sr = 44100; rnd = random.Random(seed); n = int(sr * dur); out = []; y = 0.0
    for i in range(n):
        t = i / sr
        env = math.exp(-t * decay)
        y += lp * ((rnd.random() * 2 - 1) - y)           # one-pole low-pass: takes the hiss off
        body = math.sin(2 * math.pi * tone_hz * t) * math.exp(-t * decay * 1.5)
        out.append(vol * env * (noise_amp * y + tone_amp * body))
    fade = 64
    for i in range(fade): out[-1 - i] *= i / fade
    return out, sr

def ambient(seconds=48, sr=22050):
    """Warm, bright, slow ambient loop (original). Every note is added with wrap-around, so the
    end of the file flows straight into the start: the loop has no seam."""
    n = int(seconds * sr); out = [0.0] * n
    def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
    # Original progression in D major, 8 s per chord: Dmaj9, F#m7, Gmaj7, Em9, Bm7, A(sus2)
    chords = [[50, 62, 66, 69, 73, 76], [54, 61, 64, 69, 73], [55, 62, 66, 71, 74],
              [52, 62, 66, 67, 71], [47, 62, 66, 69, 73], [45, 61, 64, 69, 71]]
    seg = n // len(chords)

    def note(start, length, f, amp, attack, release, bell=False):
        """Soft electric-piano/pad tone: few harmonics, smooth envelope (no detune beating)."""
        total = length + release
        for k in range(total):
            t = k / sr
            if bell:
                env = min(1.0, t / 0.01) * math.exp(-t * 2.2)
            else:
                env = min(1.0, t / attack) * (1.0 if k < length else max(0.0, 1 - (k - length) / release))
            v = (math.sin(2 * math.pi * f * t) + 0.18 * math.sin(4 * math.pi * f * t)
                 + 0.05 * math.sin(6 * math.pi * f * t))
            out[(start + k) % n] += amp * env * v

    rnd = random.Random(11)
    for ci, ch in enumerate(chords):
        s0 = ci * seg
        note(s0, seg, hz(ch[0] - 12), 0.30, 1.2 * sr / sr, int(2.5 * sr))          # soft bass
        for m in ch[1:]:
            note(s0, seg, hz(m), 0.16, 1.6, int(2.5 * sr))                           # pad chord
        # Gentle bell sparkles from the chord, an octave up, every ~1.33 s.
        for b in range(6):
            m = rnd.choice(ch[1:]) + 12
            note(s0 + int(b * seg / 6) + rnd.randint(0, sr // 8), 1, hz(m), 0.10, 0.01, int(2.4 * sr), bell=True)
    # Very light smoothing (warmer top end), applied circularly so the seam stays clean.
    for _ in range(2):
        prev = out[-1]
        for i in range(n):
            cur = out[i]; out[i] = 0.6 * cur + 0.4 * prev; prev = cur
    peak = max(abs(x) for x in out) or 1
    return [0.5 * x / peak for x in out], sr

if __name__ == '__main__':
    os.makedirs(RAW, exist_ok=True)
    for old in ('sfx_slide.wav',):
        p = os.path.join(RAW, old)
        if os.path.exists(p): os.remove(p)
    write('sfx_tick.wav', *click(0.018, 420, 1700, 0.25, 0.9, 0.35, 0.55, 1))   # scroll
    write('sfx_ok.wav',   *click(0.040, 160, 900, 0.45, 0.8, 0.25, 0.75, 2))    # select
    write('sfx_back.wav', *click(0.030, 220, 600, 0.40, 0.7, 0.20, 0.6, 3))     # back
    write('wave_ambient.wav', *ambient())
