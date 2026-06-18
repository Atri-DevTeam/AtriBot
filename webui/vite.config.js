import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ command }) => {
    return {
        plugins: [vue()],
        base: command === 'build'
            ? '/webui/'
            : '/', // dev 环境
        build: {
            outDir: '../src/main/resources/official-webui',
            emptyOutDir: true,
        },
        server: {//http://127.0.0.1:8080 /
            https: true,
            proxy: {
                // '/webui': {
                //     target: 'http://127.0.0.1:8851/', // 后端地址
                //     changeOrigin: true,
                // }
            },
            // cors: {
            //     origin: '*',
            //     methods: ['GET', 'POST', 'PUT', 'DELETE'],
            //     allowedHeaders: ['Content-Type', 'Authorization'],
            // },
        }
    }

});
