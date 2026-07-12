import { createApp } from 'vue'
import App from './App.vue'
import router from './router.js'
import './styles/base.css'
import './styles/layout.css'
import './styles/responsive.css'
import './styles/feedback.css'
import './styles/userlist.css'
import './styles/napcat.css'
import './styles/debug.css'

createApp(App).use(router).mount('#app')
