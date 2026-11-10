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
    """Slow, soft chord pad that loops seamlessly (original progression)."""
    chords = [  # frequencies in Hz
        [130.81, 196.00, 246.94, 293.66, 329.63],   # Cmaj9-ish
        [110.00, 164.81, 196.00, 246.94, 261.63],   # Am9-ish
        [87.31, 130.81, 174.61, 220.00, 329.63],    # Fmaj7(9)-ish
        [98.00, 146.83, 196.00, 246.94, 293.66],    # G(add9)
    ]
    n = int(seconds * sr); seg = n // len(chords); xf = int(sr * 3.0)
    out = [0.0] * n
    for ci, ch in enumerate(chords):
        start = ci * seg
        for k in range(seg + xf):
            idx = (start + k) % n                     # wraps: the last chord fades into the first
            t = k / sr
            a = min(1.0, k / xf) * min(1.0, (seg + xf - k) / xf)
            v = 0.0
            for j, f in enumerate(ch):
                lfo = 1 + 0.003 * math.sin(2 * math.pi * (0.07 + 0.013 * j) * t)
                v += math.sin(2 * math.pi * f * lfo * t + j) * (0.9 if j == 0 else 0.55)
                v += 0.12 * math.sin(2 * math.pi * f * 2.0 * t + 0.5 * j)   # gentle shimmer
            out[idx] += a * v
    peak = max(abs(s) for s in out) or 1
    return [0.32 * s / peak for s in out], sr

if __name__ == '__main__':
    os.makedirs(RAW, exist_ok=True)
    for old in ('sfx_slide.wav',):
        p = os.path.join(RAW, old)
        if os.path.exists(p): os.remove(p)
    write('sfx_tick.wav', *click(0.018, 420, 1700, 0.25, 0.9, 0.35, 0.55, 1))   # scroll
    write('sfx_ok.wav',   *click(0.040, 160, 900, 0.45, 0.8, 0.25, 0.75, 2))    # select
    write('sfx_back.wav', *click(0.030, 220, 600, 0.40, 0.7, 0.20, 0.6, 3))     # back
    write('wave_ambient.wav', *ambient())
