import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import 'katex/dist/katex.min.css'
import renderMathInElement from 'katex/dist/contrib/auto-render.mjs'
;(window as any).renderMathInElement = renderMathInElement

import './styles/variables.css'
import './styles/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
