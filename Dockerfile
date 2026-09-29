FROM node:24-alpine
WORKDIR /app
COPY scripts/serve.mjs ./scripts/serve.mjs
COPY prototype ./prototype
USER node
ENV HOST=0.0.0.0 PORT=4173 STOCKFLOW_CONTAINER=1
EXPOSE 4173
CMD ["node", "scripts/serve.mjs"]
