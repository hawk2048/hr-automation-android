# Test Audio Directory

This directory contains test audio files for benchmarking speech models.

## Included Test Audio

1. `test_audio_en_5s.wav` - 5 second English audio sample for Whisper testing
2. `test_audio_zh_5s.wav` - 5 second Chinese audio sample for Paraformer testing
3. `test_silence_1s.wav` - 1 second silence for VAD testing
4. `test_speech_mixed.wav` - Mixed speech/silence for VAD accuracy testing

## Audio Specifications

- Sample Rate: 16000 Hz (required for most speech models)
- Channels: Mono
- Format: 16-bit PCM WAV
- Duration: 1-10 seconds per file

## Purpose

These audio files are used for:
- STT (Speech-to-Text) model benchmarking
- VAD (Voice Activity Detection) testing
- Speaker recognition testing

## Recording Custom Audio

Users can record custom audio directly in the app during benchmarking.
The app will automatically convert and resample audio to match model requirements.
