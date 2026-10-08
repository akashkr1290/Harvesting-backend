"""Downloads the vision models into HF_HOME at image build time."""
import os

from transformers import CLIPModel, CLIPProcessor, pipeline

clip = os.environ.get("CLIP_MODEL", "openai/clip-vit-base-patch32")
CLIPModel.from_pretrained(clip)
CLIPProcessor.from_pretrained(clip)
print("CLIP ready:", clip)

detector = os.environ.get("AI_IMAGE_DETECTOR_MODEL", "")
if detector:
    pipeline("image-classification", model=detector)
    print("AI-image detector ready:", detector)
else:
    print("No AI_IMAGE_DETECTOR_MODEL set: only CLIP crop matching will run.")
