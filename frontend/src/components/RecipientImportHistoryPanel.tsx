import { useEffect, useRef, useState } from 'react'
import { getImportHistory } from '../api/imports'
import type { ImportStatus } from '../api/imports'
import RecipientImportPanel from './RecipientImportPanel'

type Props = {
    campaignId: number
}

export default function RecipientImportHistoryPanel({
                                                        campaignId,
                                                    }: Props) {
    const [imports, setImports] = useState<ImportStatus[]>([])
    const [hasMore, setHasMore] = useState(false)
    const [nextBeforeId, setNextBeforeId] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [selectedImportId, setSelectedImportId] =
        useState<number | null>(null)

    const [request, setRequest] = useState({
        beforeId: 0,
        version: 0,
    })

    const requestLock = useRef(true)

    useEffect(() => {
        const controller = new AbortController()

        async function load() {
            try {
                const result = await getImportHistory(
                    campaignId,
                    request.beforeId,
                    controller.signal,
                )

                if (controller.signal.aborted) return

                if (
                    result.campaignId !== campaignId ||
                    !Array.isArray(result.imports) ||
                    typeof result.hasMore !== 'boolean' ||
                    !Number.isSafeInteger(result.nextBeforeId) ||
                    result.nextBeforeId < 0
                ) {
                    throw new Error('Import history response is invalid.')
                }

                let previousId =
                    request.beforeId === 0 ? Infinity : request.beforeId

                for (const item of result.imports) {
                    if (
                        !item ||
                        item.campaignId !== campaignId ||
                        !Number.isSafeInteger(item.importId) ||
                        item.importId <= 0 ||
                        item.importId >= previousId ||
                        typeof item.originalFilename !== 'string' ||
                        typeof item.status !== 'string'
                    ) {
                        throw new Error('Import history contains an invalid record.')
                    }

                    const counts = [
                        item.processedRows,
                        item.addedRows,
                        item.duplicateRows,
                        item.invalidRows,
                    ]

                    if (
                        counts.some(
                            (value) => !Number.isSafeInteger(value) || value < 0,
                        )
                    ) {
                        throw new Error('Import history contains invalid counts.')
                    }

                    previousId = item.importId
                }

                if (
                    result.hasMore &&
                    (result.imports.length === 0 ||
                        result.nextBeforeId !==
                        result.imports[result.imports.length - 1].importId)
                ) {
                    throw new Error('Import history cursor is invalid.')
                }

                setImports((current) => {
                    if (request.beforeId === 0) return result.imports

                    const existingIds = new Set(
                        current.map((item) => item.importId),
                    )

                    return [
                        ...current,
                        ...result.imports.filter(
                            (item) => !existingIds.has(item.importId),
                        ),
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
                        : 'Could not load import history.',
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
    }, [campaignId, request])

    function requestPage(beforeId: number) {
        if (requestLock.current) return

        requestLock.current = true
        setLoading(true)
        setError('')

        setRequest((current) => ({
            beforeId,
            version: current.version + 1,
        }))
    }

    return (
        <section
            className="panel import-panel"
            aria-labelledby="import-history-title"
        >
            <div className="panel-heading">
                <div>
                    <h2 id="import-history-title">Import history</h2>
                    <p className="muted">
                        Previous CSV uploads for campaign #{campaignId}.
                    </p>
                </div>

                <button
                    type="button"
                    className="button secondary"
                    disabled={loading}
                    onClick={() => requestPage(0)}
                >
                    Refresh history
                </button>
            </div>

            <div className="import-content">
                {error && (
                    <div className="feedback error-feedback" role="alert">
                        <p>{error}</p>
                        <button
                            type="button"
                            className="button secondary"
                            disabled={loading}
                            onClick={() => requestPage(request.beforeId)}
                        >
                            Retry
                        </button>
                    </div>
                )}

                {imports.length > 0 && (
                    <div className="campaign-table-scroll">
                        <table className="campaign-table">
                            <caption>
                                Latest uploads first. Counts reflect the last history refresh.
                            </caption>

                            <thead>
                            <tr>
                                <th scope="col">Import</th>
                                <th scope="col">File</th>
                                <th scope="col">Status</th>
                                <th scope="col">Added</th>
                                <th scope="col">Duplicates</th>
                                <th scope="col">Invalid</th>
                                <th scope="col">Action</th>
                            </tr>
                            </thead>

                            <tbody>
                            {imports.map((item) => (
                                <tr key={item.importId}>
                                    <td>#{item.importId}</td>
                                    <td>{item.originalFilename}</td>
                                    <td>
                                        <span className="pill">{item.status}</span>
                                    </td>
                                    <td>{item.addedRows.toLocaleString()}</td>
                                    <td>{item.duplicateRows.toLocaleString()}</td>
                                    <td>{item.invalidRows.toLocaleString()}</td>
                                    <td>
                                        <button
                                            type="button"
                                            className="button secondary"
                                            aria-label={`View import ${item.importId}`}
                                            aria-pressed={selectedImportId === item.importId}
                                            onClick={() => setSelectedImportId(item.importId)}
                                        >
                                            {selectedImportId === item.importId
                                                ? 'Selected'
                                                : 'View import'}
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {loading && <p role="status">Loading import history...</p>}

                {!loading && !error && imports.length === 0 && (
                    <p>No CSV uploads found for this campaign.</p>
                )}

                {!error && hasMore && (
                    <button
                        type="button"
                        className="button secondary"
                        disabled={loading}
                        onClick={() => requestPage(nextBeforeId)}
                    >
                        {loading ? 'Loading...' : 'Load more imports'}
                    </button>
                )}

                {!loading && !error && imports.length > 0 && !hasMore && (
                    <p className="draft-note">All imports loaded.</p>
                )}

                <p className="draft-note">
                    After a new upload, use Refresh history to update this list.
                    Open an import to follow its current status and inspect invalid rows.
                </p>

                {selectedImportId !== null && (
                    <div>
                        <button
                            type="button"
                            className="button secondary"
                            onClick={() => setSelectedImportId(null)}
                        >
                            Close import details
                        </button>

                        <RecipientImportPanel
                            key={`${campaignId}-${selectedImportId}`}
                            campaignId={campaignId}
                            initialImportId={selectedImportId}
                        />
                    </div>
                )}
            </div>
        </section>
    )
}