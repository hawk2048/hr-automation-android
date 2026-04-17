# Test Images Directory

This directory contains test images for benchmarking image models.

## Included Test Images

1. `test_classification_224x224.png` - Standard 224x224 test image for classification models (MobileNet, EfficientNet, ViT)
2. `test_ocr_sample.png` - Sample image with text for OCR testing
3. `test_objects.jpg` - Image with common objects for classification

## Purpose

These images are used for:
- Image classification model benchmarking
- OCR (Optical Character Recognition) testing
- VLM (Vision Language Model) testing

## Adding Custom Test Images

Users can select custom images from their gallery or camera during benchmarking.
The benchmark will automatically resize images to match model input requirements.
