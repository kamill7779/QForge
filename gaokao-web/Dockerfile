FROM node:20-alpine AS build
WORKDIR /app

ARG VITE_GAOKAO_MOCK=false
ENV VITE_GAOKAO_MOCK=${VITE_GAOKAO_MOCK}

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80