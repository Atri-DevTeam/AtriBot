import {createApp} from 'vue'
import App from './App.vue'
import Dashboard from './Dashboard.vue'
import './styles.css'
import './activity.css'
import './intro.css'

const qq = new URLSearchParams(window.location.search).getAll('qq')
document.documentElement.classList.toggle('miniapp-qq', qq.length === 1 && qq[0] === '1')

const dashboard = /^\/dashboard(?:\/|$)/.test(window.location.pathname)
createApp(dashboard ? Dashboard : App).mount('#app')
