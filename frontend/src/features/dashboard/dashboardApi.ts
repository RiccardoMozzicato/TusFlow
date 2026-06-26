import { http } from '@/api/axios'
import { API } from '@/config'

// Aggregate job counters from GET /api/v1/jobs/stats (AIProcessingService).
export interface JobStatsDTO {
  /** PENDING + PROCESSING jobs. */
  pending: number
  /** Jobs COMPLETED at or after the `since` instant sent with the request. */
  completedSince: number
  failed: number
}

/**
 * Fetches aggregate job counters. Pass `since` (ISO instant) to scope the
 * completed count, e.g. the start of the local day for "completed today".
 */
export async function getJobStats(since?: string): Promise<JobStatsDTO> {
  const { data } = await http.get<JobStatsDTO>(`${API.jobs}/stats`, {
    params: since ? { since } : undefined,
  })
  return data
}
