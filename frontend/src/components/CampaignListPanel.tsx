import { useEffect, useRef, useState } from 'react'

type Campaign = {
    id: number
    name: string
    subject: string
    status: string
}

type CampaignPage = {
    campaigns: Campaign[]
    hasMore: boolean
    nextBeforeId: number
}

type Props = {
    onOpen: (id: number) => void
    opening: boolean
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

async function fetchPage(
    beforeId: number,
    signal: AbortSignal,
): Promise<CampaignPage> {
    const response = await fetch(
        `/api/v1/campaigns?beforeId=${beforeId}&limit=20`,
        { signal, cache: 'no-store' },
    )

    const data: unknown = await response.json()

    if (!response.ok) {
        throw new Error(
            isObject(data) && typeof data.message === 'string'
                ? data.message
                : `Could not load campaigns (${response.status}).`,
        )
    }

    if (
        !isObject(data) ||
        !Array.isArray(data.campaigns) ||
        typeof data.hasMore !== 'boolean' ||
        typeof data.nextBeforeId !== 'number' ||
        !Number.isSafeInteger(data.nextBeforeId) ||
        data.nextBeforeId < 0
    ) {
        throw new Error('Campaign list response is invalid.')
    }

    const campaigns: Campaign[] = []
    let previousId = beforeId === 0 ? Infinity : beforeId

    for (const item of data.campaigns) {
        if (
            !isObject(item) ||
            typeof item.id !== 'number' ||
            !Number.isSafeInteger(item.id) ||
            item.id <= 0 ||
            item.id >= previousId ||
            typeof item.name !== 'string' ||
            typeof item.subject !== 'string' ||
            typeof item.status !== 'string'
        ) {
            throw new Error('Campaign list contains an invalid record or order.')
        }

        campaigns.push({
            id: item.id,
            name: item.name,
            subject: item.subject,
            status: item.status,
        })

        previousId = item.id
    }

    if (
        data.hasMore &&
        (campaigns.length === 0 ||
            data.nextBeforeId !== campaigns[campaigns.length - 1].id)
    ) {
        throw new Error('Campaign pagination cursor is invalid.')
    }

    return {
        campaigns,
        hasMore: data.hasMore,
        nextBeforeId: data.nextBeforeId,
    }
}

export default function CampaignListPanel({ onOpen, opening }: Props) {
    const [campaigns, setCampaigns] = useState<Campaign[]>([])
    const [hasMore, setHasMore] = useState(false)
    const [nextBeforeId, setNextBeforeId] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [request, setRequest] = useState({ beforeId: 0, version: 0 })

    const requestLock = useRef(true)

    useEffect(() => {
        const controller = new AbortController()

        async function load() {
            try {
                const result = await fetchPage(
                    request.beforeId,
                    controller.signal,
                )

                if (controller.signal.aborted) return

                setCampaigns((current) => {
                    if (request.beforeId === 0) return result.campaigns

                    const existingIds = new Set(current.map((item) => item.id))

                    return [
                        ...current,
                        ...result.campaigns.filter((item) => !existingIds.has(item.id)),
                    ]
                })

                setHasMore(result.hasMore)
                setNextBeforeId(result.nextBeforeId)
                setError('')
            } catch (error) {
                if (controller.signal.aborted) return

                setError(
                    error instanceof Error
                        ? error.message
                        : 'Could not load campaigns.',
                )
            } finally {
                if (!controller.signal.aborted) {
                    requestLock.current = false
                    setLoading(false)
                }
            }
        }

        void load()

        return () => controller.abort()
    }, [request])

    function requestPage(beforeId: number) {
        if (requestLock.current || opening) return

        requestLock.current = true
        setLoading(true)
        setError('')
        setRequest((current) => ({
            beforeId,
            version: current.version + 1,
        }))
    }

    return (
        <section className="panel campaign-list-panel" aria-labelledby="list-title">
            <div className="panel-heading">
                <div>
                    <h2 id="list-title">Saved campaigns</h2>
                    <p className="muted">Newest campaigns first.</p>
                </div>

                <button
                    type="button"
                    className="button secondary"
                    disabled={loading || opening}
                    onClick={() => requestPage(0)}
                >
                    Refresh list
                </button>
            </div>

            <div className="campaign-list-content" aria-busy={loading}>
                {error && (
                    <div className="feedback error-feedback" role="alert">
                        <p>{error}</p>
                        <button
                            type="button"
                            className="button secondary"
                            disabled={loading || opening}
                            onClick={() => requestPage(request.beforeId)}
                        >
                            Retry
                        </button>
                    </div>
                )}

                {campaigns.length > 0 && (
                    <div className="campaign-table-scroll">
                        <table className="campaign-table">
                            <caption>Saved campaigns and their latest loaded status</caption>
                            <thead>
                            <tr>
                                <th scope="col">ID</th>
                                <th scope="col">Campaign</th>
                                <th scope="col">Subject</th>
                                <th scope="col">Status</th>
                                <th scope="col">Action</th>
                            </tr>
                            </thead>
                            <tbody>
                            {campaigns.map((campaign) => (
                                <tr key={campaign.id}>
                                    <td>#{campaign.id}</td>
                                    <td>{campaign.name}</td>
                                    <td>{campaign.subject}</td>
                                    <td>
                      <span className="pill">
                        {campaign.status.replaceAll('_', ' ')}
                      </span>
                                    </td>
                                    <td>
                                        <button
                                            type="button"
                                            className="button secondary"
                                            disabled={opening || loading}
                                            aria-label={`View campaign ${campaign.id}: ${campaign.name}`}
                                            onClick={() => onOpen(campaign.id)}
                                        >
                                            View campaign
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {loading && <p role="status">Loading campaigns...</p>}

                {!loading && !error && campaigns.length === 0 && (
                    <p>No campaigns yet. Create your first campaign above.</p>
                )}

                {!error && hasMore && (
                    <button
                        type="button"
                        className="button secondary"
                        disabled={loading || opening}
                        onClick={() => requestPage(nextBeforeId)}
                    >
                        {loading ? 'Loading...' : 'Load more'}
                    </button>
                )}

                {!loading && !error && campaigns.length > 0 && !hasMore && (
                    <p className="draft-note">All campaigns loaded.</p>
                )}
            </div>
        </section>
    )
}