import MarkdownIt from 'markdown-it'
import type { RenderRule } from 'markdown-it/lib/renderer.mjs'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import DOMPurify from 'dompurify'

hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('yaml', yaml)

export interface HeadingItem {
  id: string
  text: string
  level: number
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function plainText(content: string): string {
  return content
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[*_`~]/g, '')
    .trim()
}

export function headingId(text: string): string {
  const normalized = plainText(text)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
  return normalized || 'section'
}

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: false,
  highlight(str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch {
        /* fall through */
      }
    }
    return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`
  },
})

const defaultHeadingOpen: RenderRule | undefined = md.renderer.rules.heading_open
const headingOpen: RenderRule = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const inline = tokens[idx + 1]
  if (inline && inline.type === 'inline') {
    token.attrSet('id', headingId(inline.content))
  }
  if (defaultHeadingOpen) {
    return defaultHeadingOpen(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.heading_open = headingOpen

const defaultLinkOpen: RenderRule | undefined = md.renderer.rules.link_open
const linkOpen: RenderRule = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet('href') || ''
  if (/^https?:\/\//i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  if (defaultLinkOpen) {
    return defaultLinkOpen(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.link_open = linkOpen

export function renderMarkdown(content: string): string {
  const raw = md.render(content || '')
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ['target', 'rel', 'id'],
  })
}

export function extractHeadings(content: string): HeadingItem[] {
  const lines = (content || '').split('\n')
  const headings: HeadingItem[] = []
  let inCodeBlock = false
  const used = new Map<string, number>()
  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) continue
    const match = /^(#{1,3})\s+(.+?)\s*#*\s*$/.exec(line)
    if (!match) continue
    const text = plainText(match[2])
    if (!text) continue
    const base = headingId(match[2])
    const count = used.get(base) || 0
    used.set(base, count + 1)
    headings.push({
      id: count === 0 ? base : `${base}-${count}`,
      text,
      level: match[1].length,
    })
  }
  return headings
}
