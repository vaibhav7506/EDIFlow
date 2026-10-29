import { existsSync, readFileSync } from 'node:fs'
import { Agent } from 'node:https'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  let gatewayProxy: ProxyOptions | undefined

  if (command === 'serve' && mode !== 'e2e' && env.VITE_DASHBOARD_MODE === 'live') {
    const certificateDirectory = fileURLToPath(new URL('../.certs/', import.meta.url))
    const clientCertificate = `${certificateDirectory}dashboard-client.p12`
    const certificateAuthority = `${certificateDirectory}ca.crt`

    if (!existsSync(clientCertificate) || !existsSync(certificateAuthority)) {
      throw new Error('Development proxy certificates are missing. Run the cert-generator service first.')
    }

    gatewayProxy = {
      target: env.VITE_GATEWAY_URL || 'https://localhost:8443',
      changeOrigin: true,
      secure: true,
      agent: new Agent({
        pfx: readFileSync(clientCertificate),
        passphrase: env.TLS_STORE_PASSWORD || 'changeit',
        ca: readFileSync(certificateAuthority),
      }),
    }
  }

  return {
    plugins: [react()],
    server: gatewayProxy
      ? {
          proxy: {
            '/api': gatewayProxy,
            '/oauth2': gatewayProxy,
            '/.well-known': gatewayProxy,
          },
        }
      : undefined,
  }
})
