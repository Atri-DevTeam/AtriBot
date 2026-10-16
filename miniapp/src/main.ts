import {createApp} from 'vue'
import App from './App.vue'
import Dashboard from './Dashboard.vue'
import './styles.css'
import './activity.css'
import './intro.css'

const dashboard = /^\/dashboard\/?$/.test(window.location.pathname)
createApp(dashboard ? Dashboard : App).mount('#app')
