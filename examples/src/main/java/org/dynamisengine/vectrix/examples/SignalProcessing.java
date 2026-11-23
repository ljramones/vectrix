// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.dsp.Biquad;
import org.dynamisengine.vectrix.dsp.BiquadDesign;
import org.dynamisengine.vectrix.dsp.FirDesign;
import org.dynamisengine.vectrix.dsp.Spectra;
import org.dynamisengine.vectrix.dsp.Windows;
import org.dynamisengine.vectrix.fft.BlockConvolverd;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.dynamisengine.vectrix.hash.Philox;

/**
 * Find the tones in a noisy signal with Welch's spectral estimate, then remove one of them with a linear-phase FIR
 * low-pass (streamed block by block) and with a Butterworth IIR low-pass.
 * <p>
 * Frequencies in the {@code dsp} package are in cycles per sample: divide hertz by the sample rate. Plans (FFT,
 * block convolver) are made once and reused; every call takes caller scratch, so the processing loop does not
 * allocate.
 */
public final class SignalProcessing {

    /** Tone powers (dB) at 50 Hz and 120 Hz before and after each filter. */
    public record Result(int peakBinHz, double tone50Db, double tone120Db, double fir50Db, double fir120Db,
            double iir50Db, double iir120Db, int firTaps) {
    }

    static final double RATE = 1000; // samples per second
    static final int LENGTH = 16_384;
    static final int SEGMENT = 1024; // Welch segment: 1000/1024 ≈ 0.98 Hz per bin

    private SignalProcessing() {
    }

    public static Result run() {
        // A 50 Hz tone, a weaker 120 Hz tone and white noise. Philox gives the same noise on every machine.
        double[] x = new double[LENGTH];
        float[] noise = new float[LENGTH];
        Philox.fillNormal(42, 0, 0f, 0.1f, noise, 0, LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            double t = i / RATE;
            x[i] = Math.sin(2 * Math.PI * 50 * t) + 0.5 * Math.sin(2 * Math.PI * 120 * t) + noise[i];
        }

        // tag::spectrum[]
        // Welch's estimate: Hann-windowed segments of 1024 samples, overlapping by half, averaged.
        RealFftd plan = RealFftd.plan(SEGMENT);
        double[] window = new double[SEGMENT];
        Windows.hann(window, 0, SEGMENT, true);
        double[] psd = new double[SEGMENT / 2 + 1];
        double[] scratch = new double[Spectra.scratchSize(plan)];
        Spectra.welch(plan, window, 0, SEGMENT / 2, x, 0, LENGTH, psd, 0, scratch, 0);

        int peak = 0;
        for (int k = 1; k < psd.length; k++) {
            peak = psd[k] > psd[peak] ? k : peak;
        }
        double peakHz = peak * RATE / SEGMENT; // bin k is k·rate/segment hertz
        // end::spectrum[]

        // tag::fir[]
        // Low-pass at 80 Hz: Kaiser window for 60 dB of stop-band attenuation, 20 Hz transition band.
        double attenuation = 60;
        double transition = 20 / RATE;
        int taps = FirDesign.kaiserTaps(attenuation, transition) | 1; // odd length: integer delay
        double[] kaiser = new double[taps];
        Windows.kaiser(kaiser, 0, taps, FirDesign.kaiserBeta(attenuation), false);
        double[] h = new double[taps];
        FirDesign.lowpass(h, 0, taps, 90 / RATE, kaiser, 0); // cutoff mid-transition: 80 to 100 Hz

        // Stream the signal through the filter in blocks of 256 samples, as audio would arrive.
        int block = 256;
        BlockConvolverd fir = BlockConvolverd.plan(h, 0, taps, block);
        double[] state = new double[fir.stateSize()];
        double[] firScratch = new double[fir.scratchSize()];
        double[] firOut = new double[LENGTH];
        for (int off = 0; off < LENGTH; off += block) {
            fir.process(x, off, Math.min(block, LENGTH - off), firOut, off, state, 0, firScratch, 0);
        }
        // end::fir[]

        // tag::iir[]
        // The same job with a 6th-order Butterworth low-pass at 80 Hz: three second-order sections, far fewer
        // operations than the FIR, but with a non-linear phase.
        double[] sos = new double[BiquadDesign.butterworthSections(6) * BiquadDesign.STRIDE];
        int sections = BiquadDesign.butterworthLowpass(sos, 0, 6, 80 / RATE);
        double[] iirState = new double[sections * Biquad.STATE_STRIDE];
        double[] iirOut = new double[LENGTH];
        Biquad.process(sos, 0, sections, iirState, 0, x, 0, iirOut, 0, LENGTH);
        // end::iir[]

        double[] firPsd = new double[psd.length];
        double[] iirPsd = new double[psd.length];
        // skip the start-up transient of the filters
        Spectra.welch(plan, window, 0, SEGMENT / 2, firOut, 2048, LENGTH - 2048, firPsd, 0, scratch, 0);
        Spectra.welch(plan, window, 0, SEGMENT / 2, iirOut, 2048, LENGTH - 2048, iirPsd, 0, scratch, 0);
        return new Result((int) Math.round(peakHz), db(psd, 50), db(psd, 120), db(firPsd, 50), db(firPsd, 120),
            db(iirPsd, 50), db(iirPsd, 120), taps);
    }

    /** The power within ±2 bins of {@code hz}, in dB. */
    static double db(double[] psd, double hz) {
        int k = (int) Math.round(hz * SEGMENT / RATE);
        double sum = 0;
        for (int j = k - 2; j <= k + 2; j++) {
            sum += psd[j];
        }
        return 10 * Math.log10(sum / SEGMENT);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println("strongest tone: " + r.peakBinHz() + " Hz");
        System.out.printf(java.util.Locale.ROOT, "%-24s %8s %8s%n", "", "50 Hz", "120 Hz");
        System.out.printf(java.util.Locale.ROOT, "%-24s %8.1f %8.1f dB%n", "input", r.tone50Db(), r.tone120Db());
        System.out.printf(java.util.Locale.ROOT, "%-24s %8.1f %8.1f dB%n", "FIR low-pass (" + r.firTaps() + " taps)",
            r.fir50Db(), r.fir120Db());
        System.out.printf(java.util.Locale.ROOT, "%-24s %8.1f %8.1f dB%n", "Butterworth, order 6", r.iir50Db(),
            r.iir120Db());
    }
}
