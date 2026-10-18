import { useEffect, useRef, useState } from 'react'

type CampaignProgress = {
    campaignId: number
    status: string
    totalRecipients: number
    pending: number
    sending: number
    retry: number
    sent: number
    failed: number
    processedRecipients: number
    progressPercent: number
}

type Props = {
    campaignId: number
    onStatusChange: (campaignId: number, status: string) => void
}

const terminalStatuses = new Set([
    'COMPLETED',
    'COMPLETED_WITH_ERRORS',
])

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

async function readResponse(response: Response): Promise<unknown> {
    let data: unknown

    try {
        data = await response.json()
    } catch {
        throw new Error(`Server returned an unreadable response (${response.status}).`)
    }

    if (!response.ok) {
        throw new Error(
            isObject(data) && typeof data.message === 'string'
                ? data.message
                : `Request failed (${response.status}).`,
        )
    }

    return data
}

function parseProgress(data: unknown, campaignId: number): CampaignProgress {
    if (
        !isObject(data) ||
        data.campaignId !== campaignId ||
        typeof data.status !== 'string'
    ) {
        throw new Error('Campaign progress response is invalid.')
    }

    const countFields = [
        'totalRecipients',
        'pending',
        'sending',
        'retry',
        'sent',
        'failed',
        'processedRecipients',
    ] as const

    for (const field of countFields) {
        const value = data[field]

        if (
            typeof value !== 'number' ||
            !Number.isSafeInteger(value) ||
            value < 0
        ) {
            throw new Error(`Invalid campaign count: ${field}.`)
        }
    }

    if (
        typeof data.progressPercent !== 'number' ||
        !Number.isFinite(data.progressPercent) ||
        data.progressPercent < 0 ||
        data.progressPercent > 100
    ) {
        throw new Error('Campaign progress percentage is invalid.')
    }

    return data as CampaignProgress
}

export default function CampaignDeliveryPanel({
                                                  campaignId,
                                                  onStatusChange,
                                              }: Props) {
    const [progress, setProgress] = useState<CampaignProgress | null>(null)
    const [loading, setLoading] = useState(true)
    const [progressError, setProgressError] = useState('')
    const [startError, setStartError] = useState('')
    const [starting, setStarting] = useState(false)
    const [confirming, setConfirming] = useState(false)
    const [refreshKey, setRefreshKey] = useState(0)

    const startLock = useRef(false)
    const startController = useRef<AbortController | null>(null)

    useEffect(() => {
        return () => startController.current?.abort()
    }, [])

    useEffect(() => {
        const controller = new AbortController()
        let timer: ReturnType<typeof setTimeout> | undefined

        async function refresh() {
            try {
                const response = await fetch(
                    `/api/v1/campaigns/${campaignId}/progress`,
                    {
                        signal: controller.signal,
                        cache: 'no-store',
                    },
                )

                const data = parseProgress(
                    await readResponse(response),
                    campaignId,
                )

                if (controller.signal.aborted) return

                setProgress(data)
                setProgressError('')
                setLoading(false)
                onStatusChange(campaignId, data.status)

                // Refresh drafts too, so imported recipient counts stay current.
                if (!terminalStatuses.has(data.status)) {
                    timer = setTimeout(() => void refresh(), 2000)
                }
            } catch (error) {
                if (controller.signal.aborted) return

                setLoading(false)
                setProgressError(
                    error instanceof Error
                        ? error.message
                        : 'Could not load campaign progress.',
                )
            }
        }

        void refresh()

        return () => {
            controller.abort()
            if (timer !== undefined) clearTimeout(timer)
        }
    }, [campaignId, refreshKey, onStatusChange])

    async function startCampaign() {
        if (
            startLock.current ||
            loading ||
            progressError ||
            progress?.status !== 'DRAFT' ||
            progress.pending === 0
        ) {
            return
        }

        startLock.current = true
        setStarting(true)
        setStartError('')

        const controller = new AbortController()
        startController.current = controller

        try {
            const response = await fetch(
                `/api/v1/campaigns/${campaignId}/start`,
                {
                    method: 'POST',
                    signal: controller.signal,
                },
            )

            const data = await readResponse(response)

            if (
                !isObject(data) ||
                data.campaignId !== campaignId ||
                data.status !== 'RUNNING'
            ) {
                throw new Error('Could not confirm the campaign start response.')
            }

            if (controller.signal.aborted) return

            onStatusChange(campaignId, 'RUNNING')
            setProgress((current) =>
                current ? { ...current, status: 'RUNNING' } : current,
            )
        } catch (error) {
            if (controller.signal.aborted) return

            setStartError(
                `${error instanceof Error ? error.message : 'Start request failed.'} ` +
                'Check the refreshed campaign status before trying again.',
            )
        } finally {
            startLock.current = false

            if (!controller.signal.aborted) {
                setStarting(false)
                setConfirming(false)
                // Reconcile server state even if the POST response was lost.
                setLoading(true)
                setRefreshKey((current) => current + 1)
            }
        }
    }

    const canStart =
        progress?.status === 'DRAFT' &&
        progress.pending > 0 &&
        !loading &&
        !progressError &&
        !starting

    return (
        <section className="panel delivery-panel" aria-labelledby="delivery-title">
            <div className="panel-heading">
                <div>
                    <p className="eyebrow">STEP 03 / SEND & TRACK</p>
                    <h2 id="delivery-title">Campaign delivery</h2>
                </div>
                <span className="pill">
          {progress?.status ?? 'LOADING'}
        </span>
            </div>

            <div className="delivery-content">
                <p className="muted">Campaign #{campaignId}</p>

                {loading && <p role="status">Checking campaign status...</p>}

                {progressError && (
                    <div className="feedback error-feedback" role="alert">
                        <p>{progressError}</p>
                        <p>Automatic refresh stopped. Displayed counts may be outdated.</p>
                        <button
                            type="button"
                            className="button secondary"
                            onClick={() => {
                                setLoading(true)
                                setProgressError('')
                                setRefreshKey((current) => current + 1)
                            }}
                        >
                            Retry refresh
                        </button>
                    </div>
                )}

                {progress && (
                    <>
                        <dl className="delivery-stats">
                            {[
                                ['Total recipients', progress.totalRecipients],
                                ['Pending', progress.pending],
                                ['Sending', progress.sending],
                                ['Retry', progress.retry],
                                ['Sent', progress.sent],
                                ['Failed', progress.failed],
                            ].map(([label, value]) => (
                                <div key={label}>
                                    <dt>{label}</dt>
                                    <dd>{value.toLocaleString()}</dd>
                                </div>
                            ))}
                        </dl>

                        <label htmlFor={`delivery-progress-${campaignId}`}>
                            Processed: {progress.processedRecipients.toLocaleString()}
                            {' / '}
                            {progress.totalRecipients.toLocaleString()}
                            {' — '}
                            {progress.progressPercent.toFixed(1)}%
                        </label>

                        <progress
                            id={`delivery-progress-${campaignId}`}
                            max={100}
                            value={progress.progressPercent}
                        />

                        <p className="draft-note">
                            Processed includes sent and permanently failed emails.
                            Sent means the SMTP server accepted the message.
                        </p>

                        {progress.status === 'DRAFT' && (
                            <>
                                <p>
                                    Review your message and imported recipients before starting.
                                    Any queued or running import must finish first.
                                </p>

                                {progress.pending === 0 && (
                                    <p className="draft-note">
                                        Import at least one valid recipient to enable sending.
                                    </p>
                                )}

                                {confirming ? (
                                    <div className="delivery-confirmation">
                                        <strong>Start campaign #{campaignId}?</strong>
                                        <p>
                                            This starts sending to the campaign recipients through
                                            your configured email server.
                                        </p>
                                        <div className="editor-actions">
                                            <button
                                                type="button"
                                                className="button primary"
                                                disabled={!canStart}
                                                onClick={() => void startCampaign()}
                                            >
                                                {starting ? 'Starting...' : 'Confirm and start'}
                                            </button>
                                            <button
                                                type="button"
                                                className="button secondary"
                                                disabled={starting}
                                                onClick={() => setConfirming(false)}
                                            >
                                                Cancel
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    <button
                                        type="button"
                                        className="button primary"
                                        disabled={!canStart}
                                        onClick={() => {
                                            setStartError('')
                                            setConfirming(true)
                                        }}
                                    >
                                        Start campaign
                                    </button>
                                )}
                            </>
                        )}

                        {progress.status === 'RUNNING' && (
                            <p role="status">
                                Sending is in progress. Counts refresh every two seconds.
                            </p>
                        )}

                        {terminalStatuses.has(progress.status) && (
                            <div className="feedback success-feedback" role="status">
                                {progress.failed > 0
                                    ? `Campaign finished with ${progress.failed} failed emails.`
                                    : 'Campaign completed successfully.'}
                            </div>
                        )}
                    </>
                )}

                {startError && (
                    <p className="feedback error-feedback" role="alert">
                        {startError}
                    </p>
                )}
            </div>
        </section>
    )
}