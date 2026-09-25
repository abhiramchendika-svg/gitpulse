import type { ApiError } from '../services/apiClient'
import { formatTime } from './format'

/** Human explanation for each backend error code. */
export function describeError(error: ApiError): string {
  switch (error.code) {
    case 'REPOSITORY_NOT_FOUND':
      return 'Repository not found. Check the name. Private repositories cannot be analysed and look the same as missing ones.'
    case 'RATE_LIMITED': {
      const when = error.resetAt ? ` It resets at ${formatTime(error.resetAt)}.` : ''
      return `GitHub API rate limit reached.${when} Configure GITHUB_TOKEN on the backend for a much higher limit.`
    }
    case 'BACKEND_UNREACHABLE':
    case 'NETWORK_ERROR':
      return 'Could not reach the GitPulse backend. Is it running?'
    case 'GITHUB_UNAVAILABLE':
      return 'GitHub is currently unreachable. Try again in a moment.'
    case 'GITHUB_AUTH_FAILED':
      return "The backend's GitHub token was rejected. Check GITHUB_TOKEN on the server."
    case 'AI_LIMIT_REACHED': {
      const when = error.resetAt ? ` Try again after ${formatTime(error.resetAt)}.` : ''
      return `This server's hourly limit for AI explanations has been reached.${when}`
    }
    case 'AI_UNAVAILABLE':
      return 'The AI service is unavailable right now. The rest of the dashboard is unaffected.'
    case 'AI_UNRELIABLE':
      return 'The AI answer did not match the numbers closely enough, so GitPulse is not showing it. You can try again.'
    default:
      return error.message || 'Something went wrong.'
  }
}
