import { createServer } from 'vite'

// In-process Vite avoids leaving npm/cmd server children running on Windows.
export default async function setup() {
  const server = await createServer({
    server: { host: '127.0.0.1' },
    plugins: [{
      name: 'test-unload-beacon',
      configureServer(vite) {
        // Chromium unload beacons can bypass Playwright routing; no real backend is used.
        vite.middlewares.use('/atrimeow/profile/api/session/close', (_request, response) => {
          response.statusCode = 204
          response.end()
        })
      }
    }]
  })
  await server.listen()
  return async () => { await server.close() }
}
