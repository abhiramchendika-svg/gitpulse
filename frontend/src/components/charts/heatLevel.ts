export const HEAT_LEVELS = 5

/**
 * Maps a count to a colour step 0..5. 0 is reserved for "no commits" (a neutral cell), so a quiet
 * hour is visibly different from a light one; non-zero counts are split into 5 equal-width bands
 * of the maximum.
 */
export function heatLevel(count: number, max: number): number {
  if (count <= 0 || max <= 0) return 0
  return Math.min(HEAT_LEVELS, Math.ceil((count / max) * HEAT_LEVELS))
}
