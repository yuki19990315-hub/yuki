FROM python:3.12-slim
WORKDIR /app
COPY server.py app.js index.html guide.html styles.css manifest.webmanifest ./
COPY assets ./assets
RUN useradd --system --uid 10001 --home-dir /app tokiha && mkdir /data && chown tokiha:tokiha /data
USER tokiha
ENV TOKIHA_DATA_DIR=/data TOKIHA_HOST=0.0.0.0 TOKIHA_SECURE_COOKIE=1 PYTHONUNBUFFERED=1
EXPOSE 8000
CMD ["python", "server.py"]
