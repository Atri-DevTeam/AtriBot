import { createApp } from 'vue'
import App from './App.vue'
import router from './router.js'
import './styles/base.css'
import './styles/layout.css'
import './styles/responsive.css'
import './styles/feedback.css'

createApp(App).use(router).mount('#app')
