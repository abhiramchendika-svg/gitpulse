import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildReport,
  downloadCsv,
  downloadJson,
  exportFileName,
  REPORT_SCHEMA_VERSION,
  slug,
  toCsv,
} from './export'

describe('toCsv', () => {
  it('writes a header and rows with CRLF line endings', () => {
    expect(
      toCsv(
        ['Week', 'Commits'],
        [
          ['2026-09-07', 3],
          ['2026-09-14', 0],
        ],
      ),
    ).toBe('Week,Commits\r\n2026-09-07,3\r\n2026-09-14,0\r\n')
  })

  it('quotes cells containing commas, quotes or line breaks', () => {
    expect(toCsv(['a', 'b', 'c'], [['x, y', 'say "hi"', 'two\nlines']])).toBe(
      'a,b,c\r\n"x, y","say ""hi""","two\nlines"\r\n',
    )
  })

  it.each([
    [
      '=HYPERLINK("http://evil.example","click")',
      `"'=HYPERLINK(""http://evil.example"",""click"")"`,
    ],
    ['+cmd', "'+cmd"],
    ['-1+1', "'-1+1"],
    ['@SUM(A1)', "'@SUM(A1)"],
    ['\tTAB', "'\tTAB"],
  ])('neutralises text that a spreadsheet would run as a formula: %s', (input, expected) => {
    expect(toCsv(['x'], [[input]])).toBe(`x\r\n${expected}\r\n`)
  })

  it('leaves numbers alone, including negative ones', () => {
    expect(toCsv(['n'], [[-5], [1.5]])).toBe('n\r\n-5\r\n1.5\r\n')
  })

  it('writes empty cells for missing values and true/false for booleans', () => {
    expect(toCsv(['a', 'b', 'c', 'd'], [[null, undefined, true, Number.NaN]])).toBe(
      'a,b,c,d\r\n,,true,\r\n',
    )
  })
})

describe('file names', () => {
  it('slugs names safely', () => {
    expect(slug('Spring-Projects/Spring_Petclinic')).toBe('spring-projects-spring_petclinic')
    expect(slug('Commits by weekday and UTC hour')).toBe('commits-by-weekday-and-utc-hour')
    expect(slug('../../etc/passwd')).toBe('..-..-etc-passwd')
    expect(slug('  ??  ')).toBe('')
  })

  it('prefixes gitpulse and appends the UTC date', () => {
    const now = new Date('2026-09-25T23:30:00Z')
    expect(exportFileName(['octocat', 'Hello-World', '90d'], 'json', now)).toBe(
      'gitpulse-octocat-hello-world-90d-20260925.json',
    )
    expect(exportFileName(['', 'x'], 'csv', now)).toBe('gitpulse-x-20260925.csv')
  })
})

describe('buildReport', () => {
  it('wraps data with provenance', () => {
    const report = buildReport('profile', 'octocat', { a: 1 }, new Date('2026-09-25T10:00:00Z'))
    expect(report).toMatchObject({
      generator: 'GitPulse',
      schemaVersion: REPORT_SCHEMA_VERSION,
      kind: 'profile',
      subject: 'octocat',
      exportedAt: '2026-09-25T10:00:00.000Z',
      data: { a: 1 },
    })
    expect(report.notes.join(' ')).toContain('docs/metrics.md')
  })
})

describe('downloads', () => {
  afterEach(() => vi.restoreAllMocks())

  function captureDownload() {
    const blobs: Blob[] = []
    const names: string[] = []
    vi.spyOn(URL, 'createObjectURL').mockImplementation((b) => {
      blobs.push(b as Blob)
      return 'blob:test'
    })
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      names.push(this.download)
    })
    return { blobs, names }
  }

  it('saves CSV with a UTF-8 byte-order mark so Excel reads names correctly', async () => {
    const { blobs, names } = captureDownload()
    downloadCsv('x.csv', ['Name'], [['Łukasz']])
    expect(names).toEqual(['x.csv'])
    expect(blobs[0].type).toBe('text/csv;charset=utf-8')
    const bytes = new Uint8Array(await blobs[0].arrayBuffer())
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    expect(new TextDecoder().decode(bytes)).toBe('Name\r\nŁukasz\r\n')
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('saves pretty-printed JSON without a byte-order mark', async () => {
    const { blobs } = captureDownload()
    downloadJson('x.json', { a: [1] })
    expect(await blobs[0].text()).toBe('{\n  "a": [\n    1\n  ]\n}\n')
  })
})
