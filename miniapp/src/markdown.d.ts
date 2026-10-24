declare module '*webui/src/shared/lib/markdown.js' {
    export function renderMarkdown(value: unknown): string
    export function escapeHtml(value: unknown): string
}

declare module '*webui/src/shared/lib/messageRender.js' {
    export function renderCommandInputTags(value: unknown): string
}
