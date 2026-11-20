# Build context: frontend/  (docker build -f frontend/Dockerfile frontend)
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first so the layer is cached across source changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# nginx:alpine is Alpine-based, so busybox wget is available in the image.
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=10 \
    CMD wget -q -O /dev/null http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
