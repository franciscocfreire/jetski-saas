# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

# Env públicas do Next embutidas no build:
# - basePath /portal: o portal vive sob o mesmo host do backoffice (nginx roteia)
# - API relativa /api: mesmo host via nginx → sem CORS
ARG NEXT_PUBLIC_BASE_PATH=/portal
ARG NEXT_PUBLIC_API_URL=/api
ENV NEXT_PUBLIC_BASE_PATH=$NEXT_PUBLIC_BASE_PATH
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Runtime stage
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production

RUN addgroup -g 1001 -S nodejs && \
    adduser -u 1001 -S nextjs -G nodejs

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

# Cache de runtime do standalone (evita EACCES — mesmo ajuste do backoffice)
RUN mkdir -p /app/.next/cache && chown -R nextjs:nodejs /app/.next

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
