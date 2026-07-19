import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ command, mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const target = env.VITE_API_TARGET || 'http://localhost:1234'

    return {
        plugins: [vue()],
        base: command === 'build'
            ? '/webui/'
            : '/', // dev 环境
        build: {
            outDir: '../src/main/resources/official-webui',
            emptyOutDir: true,
        },
        server: {
            // Vite 5 起不再内置自签证书，开 https 需额外安装 @vitejs/plugin-basic-ssl，
            // 否则 TLS 握手直接失败、dev server 无法访问。默认走 http，
            // 确实需要 https 时用 VITE_DEV_HTTPS=true 显式开启。
            https: env.VITE_DEV_HTTPS === 'true',
            proxy: {
                // 接口转发到本地 Java 后端，目标取自 .env.development 的 VITE_API_TARGET
                '/official-webui/api': { target, changeOrigin: true },
                '/webui/api': { target, changeOrigin: true },
                '/webui/meta': { target, changeOrigin: true },
            },
        }
    }

});
