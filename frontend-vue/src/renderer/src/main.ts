import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

// KaTeX: CSS + auto-render extension
import 'katex/dist/katex.min.css'
import renderMathInElement from 'katex/dist/contrib/auto-render.mjs'
;(window as any).renderMathInElement = renderMathInElement

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
