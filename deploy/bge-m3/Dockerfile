FROM python:3.10-slim

LABEL maintainer="Calmara Team"
LABEL description="BGE-M3 Embedding Service for Chinese text"

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV TRANSFORMERS_NO_TF=1
ENV HF_ENDPOINT=https://hf-mirror.com

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir \
    torch==2.1.0 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir \
    transformers==4.36.0 \
    sentence-transformers==2.2.2 \
    fastapi==0.109.0 \
    uvicorn==0.27.0 \
    pydantic==2.5.3 \
    numpy==1.26.3 \
    modelscope==1.11.0

COPY bge_m3_service.py /app/

RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('BAAI/bge-m3')"

EXPOSE 33330

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:33330/health || exit 1

CMD ["python", "-m", "uvicorn", "bge_m3_service:app", "--host", "0.0.0.0", "--port", "33330", "--workers", "1"]
