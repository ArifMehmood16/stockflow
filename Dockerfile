FROM node:24.14.1-alpine
WORKDIR /app
COPY --chown=node:node prototype ./prototype
COPY --chown=node:node scripts/serve.mjs scripts/preview-process.mjs ./scripts/
USER node
ENV HOST=0.0.0.0 PORT=4173
EXPOSE 4173
CMD ["node", "scripts/serve.mjs"]
