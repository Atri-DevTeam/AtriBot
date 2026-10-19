import { createServer } from 'vite'
import {fileURLToPath} from 'node:url'

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
  const webuiRoot = fileURLToPath(new URL('../../../webui', import.meta.url))
  const webui = await createServer({root: webuiRoot, configFile: `${webuiRoot}/vite.config.js`,
    server: {host: '127.0.0.1', port: 5175, strictPort: true}})
  await webui.listen()
  return async () => { await Promise.all([server.close(), webui.close()]) }
}
