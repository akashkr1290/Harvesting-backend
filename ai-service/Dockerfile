# HarvestFlow AI service.
#   docker build -t harvestflow-ai .                        # forecasting, routing, photo checks, mandi prices
#   docker build -t harvestflow-ai --build-arg VISION=true .  # + CLIP and AI-image detector (bigger image)
FROM python:3.13-slim

ARG VISION=false
ARG CLIP_MODEL=openai/clip-vit-base-patch32
ARG AI_IMAGE_DETECTOR_MODEL=

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AI_DATA_DIR=/data \
    HF_HOME=/models \
    CLIP_MODEL=${CLIP_MODEL} \
    AI_IMAGE_DETECTOR_MODEL=${AI_IMAGE_DETECTOR_MODEL}

WORKDIR /srv
COPY requirements.txt requirements-vision.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
 && python -c "import prophet, ortools, statsmodels; print('core ok')"

# Optional vision models, downloaded once at build time (not on every start).
COPY scripts/prefetch_models.py scripts/prefetch_models.py
RUN if [ "$VISION" = "true" ]; then \
      pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch \
   && pip install --no-cache-dir -r requirements-vision.txt \
   && python scripts/prefetch_models.py ; \
    fi
ENV VISION_ENABLED=${VISION}

COPY app app
COPY scripts scripts
RUN useradd --create-home --uid 10001 ai && mkdir -p /data /models && chown -R ai /data /models /srv
USER ai
VOLUME ["/data"]
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status==200 else 1)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
