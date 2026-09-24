import { describe, expect, it } from 'vitest'
import { heatLevel } from './heatLevel'

describe('heatLevel', () => {
  it('reserves level 0 for zero commits', () => {
    expect(heatLevel(0, 10)).toBe(0)
    expect(heatLevel(0, 0)).toBe(0)
  })

  it('never puts a non-zero count in level 0', () => {
    expect(heatLevel(1, 1000)).toBe(1)
  })

  it('puts the maximum in the top level', () => {
    expect(heatLevel(10, 10)).toBe(5)
  })

  it('splits into equal-width bands', () => {
    expect(heatLevel(2, 10)).toBe(1)
    expect(heatLevel(3, 10)).toBe(2)
    expect(heatLevel(6, 10)).toBe(3)
  })
})
