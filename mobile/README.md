# REST Mobile

Expo-приложение подключается к API через `EXPO_PUBLIC_API_URL`.
Для локального web-запуска используется `.env.local`:

```dotenv
EXPO_PUBLIC_API_URL=http://127.0.0.1:8080
```

Установка и запуск:

```bash
cd mobile
npm ci
npx expo start --web
```

Для запуска через Expo Go на телефоне:

```bash
npx expo start
```

Телефон и ноутбук должны находиться в одной сети. Для обращения к локальному backend
в `.env.local` вместо `127.0.0.1` нужно указать LAN-IP ноутбука, а API Gateway должен
принимать подключения не только с localhost.

Проверки:

```bash
npm run lint
npx tsc --noEmit
node scripts/verify-layout.mjs
node scripts/verify-api.mjs
node scripts/verify-flows.mjs
npx expo-doctor
```
