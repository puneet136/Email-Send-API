import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
    getImportErrors,
    getImportStatus,
    uploadRecipients,
} from '../api/imports'
import type { ImportRowError, ImportStatus } from '../api/imports'

type Props = {
    campaignId: number
}

const MAX_FILE_SIZE = 50 * 1024 * 1024
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED'])

function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : 'Request failed.'
}

export default function RecipientImportPanel({ campaignId }: Props) {
    const [file, setFile] = useState<File | null>(null)
    const [uploading, setUploading] = useState(false)
    const [uploadError, setUploadError] = useState('')
    const [importId, setImportId] = useState<number | null>(null)

    const [status, setStatus] = useState<ImportStatus | null>(null)
    const [statusError, setStatusError] = useState('')
    const [refreshKey, setRefreshKey] = useState(0)

    const [errors, setErrors] = useState<ImportRowError[]>([])
    const [errorsLoaded, setErrorsLoaded] = useState(false)
    const [loadingErrors, setLoadingErrors] = useState(false)
    const [errorsError, setErrorsError] = useState('')
    const [hasMore, setHasMore] = useState(false)
    const [afterRecord, setAfterRecord] = useState(0)

    const uploadLock = useRef(false)
    const errorsLock = useRef(false)
    const errorsController = useRef<AbortController | null>(null)

    useEffect(() => {
        return () => {
            errorsController.current?.abort()
        }
    }, [])

    useEffect(() => {
        if (importId === null) {
            return
        }

        const controller = new AbortController()
        let timer: ReturnType<typeof setTimeout> | undefined

        async function poll() {
            try {
                const result = await getImportStatus(
                    campaignId,
                    importId as number,
                    controller.signal,
                )

                if (controller.signal.aborted) {
                    return
                }

                setStatus(result)
                setStatusError('')

                // Schedule only after the previous request finishes.
                if (!TERMINAL_STATUSES.has(result.status)) {
                    timer = setTimeout(() => {
                        void poll()
                    }, 2000)
                }
            } catch (error) {
                if (!controller.signal.aborted) {
                    setStatusError(
                        `${errorMessage(error)} Status refresh has stopped. ` +
                        'Retry status below; do not upload the file again.',
                    )
                }
            }
        }

        void poll()

        return () => {
            controller.abort()

            if (timer !== undefined) {
                clearTimeout(timer)
            }
        }
    }, [campaignId, importId, refreshKey])

    async function handleUpload(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        if (uploadLock.current || importId !== null) {
            return
        }

        setUploadError('')

        if (!file) {
            setUploadError('Choose a CSV file first.')
            return
        }

        if (!file.name.toLowerCase().endsWith('.csv')) {
            setUploadError('Please choose a .csv file.')
            return
        }

        if (file.size === 0 || file.size > MAX_FILE_SIZE) {
            setUploadError('Choose a non-empty CSV file up to 50 MB.')
            return
        }

        uploadLock.current = true
        setUploading(true)

        try {
            const result = await uploadRecipients(campaignId, file)
            setImportId(result.importId)
        } catch (error) {
            setUploadError(
                `${errorMessage(error)} If the connection failed, the upload ` +
                'may already have been accepted. Check the backend before uploading again.',
            )
        } finally {
            uploadLock.current = false
            setUploading(false)
        }
    }

    async function loadErrors() {
        if (importId === null || errorsLock.current) {
            return
        }

        errorsLock.current = true
        setLoadingErrors(true)
        setErrorsError('')

        const controller = new AbortController()
        errorsController.current = controller

        try {
            const result = await getImportErrors(
                campaignId,
                importId,
                afterRecord,
                controller.signal,
            )

            if (controller.signal.aborted) {
                return
            }

            setErrors((previous) => [...previous, ...result.errors])
            setHasMore(result.hasMore)
            setAfterRecord(result.nextAfterRecord)
            setErrorsLoaded(true)
        } catch (error) {
            if (!controller.signal.aborted) {
                setErrorsError(errorMessage(error))
            }
        } finally {
            errorsLock.current = false

            if (!controller.signal.aborted) {
                setLoadingErrors(false)
            }
        }
    }

    const finished =
        status !== null && TERMINAL_STATUSES.has(status.status)

    return (
        <section className="panel import-panel" aria-labelledby="import-title">
            <div className="panel-heading">
                <div>
                    <p className="eyebrow">STEP 02 · RECIPIENTS</p>
                    <h2 id="import-title">Upload your recipient list</h2>
                </div>
                <span className="subtle-label">Campaign #{campaignId}</span>
            </div>

            <div className="import-content">
                {importId === null ? (
                    <form onSubmit={handleUpload} aria-busy={uploading}>
                        <label className="upload-label" htmlFor="recipient-file">
                            Choose CSV file
                        </label>

                        <p className="draft-note" id="csv-help">
                            UTF-8 CSV, up to 50 MB. Use exactly one column named
                            {' '}<code>email</code>, followed by one email per row.
                        </p>

                        <pre className="csv-example">
              {'email\npuneet@example.com\nrahul@example.com'}
            </pre>

                        <input
                            id="recipient-file"
                            type="file"
                            accept=".csv,text/csv"
                            aria-describedby="csv-help"
                            disabled={uploading}
                            onChange={(event) => {
                                setFile(event.target.files?.[0] ?? null)
                                setUploadError('')
                            }}
                        />

                        {file && (
                            <p className="draft-note">
                                Selected: {file.name}
                                {' · '}
                                {(file.size / 1024).toFixed(1)} KB
                            </p>
                        )}

                        {uploadError && (
                            <div className="feedback error-feedback" role="alert">
                                {uploadError}
                            </div>
                        )}

                        <div className="editor-actions">
                            <button
                                type="submit"
                                className="button primary"
                                disabled={uploading || !file}
                            >
                                {uploading ? 'Uploading…' : 'Upload recipients'}
                            </button>
                        </div>
                    </form>
                ) : (
                    <>
                        <div className="import-status" role="status">
                            <strong>Import #{importId}</strong>
                            <span className="pill">
                {status?.status ?? 'FETCHING STATUS'}
              </span>
                        </div>

                        <p className="draft-note">
                            {status?.originalFilename ?? file?.name}
                        </p>

                        {statusError && (
                            <div className="feedback error-feedback" role="alert">
                                <p>{statusError}</p>
                                <button
                                    type="button"
                                    className="button secondary"
                                    onClick={() => {
                                        setStatusError('')
                                        setRefreshKey((value) => value + 1)
                                    }}
                                >
                                    Retry status
                                </button>
                            </div>
                        )}

                        {status && (
                            <>
                                <dl className="import-stats">
                                    <div>
                                        <dt>Processed</dt>
                                        <dd>{status.processedRows.toLocaleString()}</dd>
                                    </div>
                                    <div>
                                        <dt>Added</dt>
                                        <dd>{status.addedRows.toLocaleString()}</dd>
                                    </div>
                                    <div>
                                        <dt>Duplicates</dt>
                                        <dd>{status.duplicateRows.toLocaleString()}</dd>
                                    </div>
                                    <div>
                                        <dt>Invalid</dt>
                                        <dd>{status.invalidRows.toLocaleString()}</dd>
                                    </div>
                                </dl>

                                {status.status === 'COMPLETED' && (
                                    <div className="feedback success-feedback" role="status">
                                        Import completed. Recipients have been processed;
                                        no emails were sent by this upload.
                                    </div>
                                )}

                                {status.status === 'FAILED' && (
                                    <div className="feedback error-feedback" role="alert">
                                        {status.lastError ?? 'Import processing failed.'}
                                        {' '}Previously committed batches may still be saved.
                                    </div>
                                )}

                                {!finished && !statusError && (
                                    <p className="draft-note">
                                        Checking progress automatically every two seconds.
                                    </p>
                                )}

                                {finished && status.invalidRows > 0 && (
                                    <div className="import-errors">
                                        <h3>Invalid records</h3>

                                        {errors.length > 0 && (
                                            <div className="table-scroll">
                                                <table>
                                                    <caption className="draft-note">
                                                        Record numbers include the CSV header.
                                                    </caption>
                                                    <thead>
                                                    <tr>
                                                        <th scope="col">Record</th>
                                                        <th scope="col">Value</th>
                                                        <th scope="col">Reason</th>
                                                    </tr>
                                                    </thead>
                                                    <tbody>
                                                    {errors.map((error) => (
                                                        <tr key={error.recordNumber}>
                                                            <td>{error.recordNumber}</td>
                                                            <td>{error.rawValue || '(empty)'}</td>
                                                            <td>{error.reason}</td>
                                                        </tr>
                                                    ))}
                                                    </tbody>
                                                </table>
                                            </div>
                                        )}

                                        {errorsError && (
                                            <p className="feedback error-feedback" role="alert">
                                                {errorsError}
                                            </p>
                                        )}

                                        {(!errorsLoaded || hasMore) && (
                                            <button
                                                type="button"
                                                className="button secondary"
                                                disabled={loadingErrors}
                                                onClick={() => void loadErrors()}
                                            >
                                                {loadingErrors
                                                    ? 'Loading…'
                                                    : errorsLoaded
                                                        ? 'Load more errors'
                                                        : 'View invalid records'}
                                            </button>
                                        )}

                                        {errorsLoaded && errors.length === 0 && (
                                            <p className="draft-note">
                                                No error details were returned.
                                            </p>
                                        )}
                                    </div>
                                )}
                            </>
                        )}
                    </>
                )}
            </div>
        </section>
    )
}