import type { QuizQuestion } from '@/api/quiz'

/**
 * 解析后端格式化后的题目 Markdown 为结构化题目列表。
 *
 * <p>后端（QuizServiceImpl）会统一输出如下格式，本函数与之一一对应：</p>
 * <pre>
 * ### 题目 1：学科名称
 *
 * 题目文本？
 *
 * A. 选项一
 * B. 选项二
 *
 * **答案：B**
 *
 * **解析：**
 * - 要点一
 * </pre>
 *
 * @param md 格式化后的题目 Markdown 文本
 * @returns 结构化题目列表；无法识别时返回空数组
 */
export function parseQuestions(md: string): QuizQuestion[] {
  const list: QuizQuestion[] = []
  if (!md) return list
  const blocks = md.split(/\n(?=#{1,3}\s*题目)/).filter(b => b.trim())
  for (const block of blocks) {
    // 标题行格式: ### 题目 N：科目名 (冒号后是科目, 不是题干)
    const titleMatch = block.match(/#{1,3}\s*题目\s*\d*[：:\s]*(.*)/)
    if (!titleMatch) continue

    // 去掉标题行, 剩余内容为题干 + 选项 + 答案
    const firstNewline = block.indexOf('\n')
    const rest = firstNewline >= 0 ? block.slice(firstNewline + 1) : ''

    // 题干: 从剩余内容开头到第一个选项行 (A. / B. ...) 之间的文本
    const firstOptIdx = rest.search(/(?:^|\n)\s*[A-D][.．、]\s/)
    const questionText = (firstOptIdx > 0 ? rest.slice(0, firstOptIdx) : rest)
      .replace(/\*\*答案[：:][\s\S]*$/, '')
      .trim()

    const opts: string[] = []
    const optMatches = rest.matchAll(/\n\s*([A-D])[.．、]\s*(.+?)(?=\n\s*[A-D][.．、]|\n\s*答案|\n\s*\*\*答案|$)/g)
    for (const m of optMatches) {
      opts.push(m[2].trim())
    }
    const answerMatch = rest.match(/\*\*答案[：:]\s*([A-D])\*\*/)
    const explainMatch = rest.match(/\*\*解析[：:]\*\*\s*([\s\S]*?)(?=\n#{1,3}\s*题目|$)/)
    if (questionText && opts.length >= 2) {
      list.push({
        question: questionText,
        options: opts,
        answer: answerMatch ? answerMatch[1] : '',
        explanation: explainMatch ? explainMatch[1].trim() : ''
      })
    }
  }
  return list
}

/** 选项序号字母（A、B、C…） */
export function optionLetter(index: number): string {
  return String.fromCharCode(65 + index)
}
