import { useCallback, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createCampaign } from './api/campaigns'
import type { CampaignResponse } from './api/campaigns'
import RecipientImportPanel from './components/RecipientImportPanel'
import CampaignDeliveryPanel from './components/CampaignDeliveryPanel'
import CampaignListPanel from './components/CampaignListPanel'
import RecipientImportHistoryPanel from './components/RecipientImportHistoryPanel'
import './App.css'

type Page = 'overview' | 'compose'

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

function App() {
    const [page, setPage] = useState<Page>('overview')

    const [name, setName] = useState('')
    const [subject, setSubject] = useState('')
    const [message, setMessage] = useState('')

    const [campaignIdToOpen, setCampaignIdToOpen] = useState('')
    const [openingCampaign, setOpeningCampaign] = useState(false)
    const [openError, setOpenError] = useState('')

    const [isSaving, setIsSaving] = useState(false)
    const [saveError, setSaveError] = useState('')

    const [savedCampaign, setSavedCampaign] =
        useState<CampaignResponse | null>(null)

    const saveInFlight = useRef(false)
    const openInFlight = useRef(false)

    const busy = isSaving || openingCampaign
    const hasUnsavedDraft =
        savedCampaign === null &&
        (name.length > 0 || subject.length > 0 || message.length > 0)

    const handleCampaignStatusChange = useCallback(
        (campaignId: number, status: string) => {
            setSavedCampaign((current) => {
                if (
                    !current ||
                    current.id !== campaignId ||
                    current.status === status
                ) {
                    return current
                }

                return { ...current, status }
            })
        },
        [],
    )

    async function handleSave(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        if (
            saveInFlight.current ||
            openInFlight.current ||
            savedCampaign !== null
        ) {
            return
        }

        setSaveError('')

        if (!name.trim() || !subject.trim() || !message.trim()) {
            setSaveError('Campaign name, subject and message are required.')
            return
        }

        if (
            name.length > 150 ||
            subject.length > 200 ||
            message.length > 10000
        ) {
            setSaveError(
                'Maximum lengths: name 150, subject 200 and message 10,000 characters.',
            )
            return
        }

        saveInFlight.current = true
        setIsSaving(true)

        try {
            const campaign = await createCampaign({
                name: name.trim(),
                subject: subject.trim(),
                message,
            })

            setSavedCampaign(campaign)
            setCampaignIdToOpen(String(campaign.id))
        } catch (error) {
            setSaveError(
                error instanceof Error
                    ? error.message
                    : 'Could not save the campaign.',
            )
        } finally {
            saveInFlight.current = false
            setIsSaving(false)
        }
    }

    function startAnotherDraft() {
        if (saveInFlight.current || openInFlight.current) return

        setName('')
        setSubject('')
        setMessage('')
        setSaveError('')
        setOpenError('')
        setSavedCampaign(null)
        setPage('compose')
    }

    function openComposer() {
        if (saveInFlight.current || openInFlight.current) return

        if (savedCampaign !== null) {
            startAnotherDraft()
            return
        }

        setOpenError('')
        setPage('compose')
    }

    function showOverview() {
        if (saveInFlight.current || openInFlight.current) return
        setPage('overview')
    }

    async function handleOpenCampaign(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        await openCampaignById(Number(campaignIdToOpen))
    }

    async function openCampaignById(id: number) {
        if (openInFlight.current || saveInFlight.current) return

        if (!Number.isSafeInteger(id) || id <= 0) {
            setOpenError('Enter a valid campaign ID.')
            return
        }

        if (
            hasUnsavedDraft &&
            !window.confirm(
                'Opening this campaign will replace your unsaved draft. Continue?',
            )
        ) {
            return
        }

        openInFlight.current = true
        setOpeningCampaign(true)
        setOpenError('')

        try {
            const response = await fetch(`/api/v1/campaigns/${id}`, {
                cache: 'no-store',
            })

            let data: unknown

            try {
                data = await response.json()
            } catch {
                throw new Error(
                    `Server returned an unreadable response (${response.status}).`,
                )
            }

            if (!response.ok) {
                throw new Error(
                    isObject(data) && typeof data.message === 'string'
                        ? data.message
                        : `Could not open campaign (${response.status}).`,
                )
            }

            if (
                !isObject(data) ||
                data.id !== id ||
                typeof data.name !== 'string' ||
                typeof data.subject !== 'string' ||
                typeof data.message !== 'string' ||
                typeof data.status !== 'string'
            ) {
                throw new Error('Campaign response has unexpected fields.')
            }

            setName(data.name)
            setSubject(data.subject)
            setMessage(data.message)
            setCampaignIdToOpen(String(id))

            setSavedCampaign({
                id,
                name: data.name,
                status: data.status,
            })

            setSaveError('')
            setPage('compose')
        } catch (error) {
            setOpenError(
                error instanceof Error
                    ? error.message
                    : 'Could not open campaign.',
            )
        } finally {
            openInFlight.current = false
            setOpeningCampaign(false)
        }
    }

    return (
        <div className="workspace">
            <a className="skip-link" href="#main-content">
                Skip to content
            </a>

            <aside className="sidebar">
                <a className="brand" href="#main-content">
          <span className="brand-mark" aria-hidden="true">
            S
          </span>

                    <span>
            SendFlow
            <span className="brand-caption">CAMPAIGN WORKSPACE</span>
          </span>
                </a>

                <p className="nav-label">WORKSPACE</p>

                <nav aria-label="Main navigation">
                    <button
                        type="button"
                        className={`nav-item ${
                            page === 'overview' || savedCampaign !== null ? 'active' : ''
                        }`}
                        aria-current={page === 'overview' ? 'page' : undefined}
                        disabled={busy}
                        onClick={showOverview}
                    >
                        <span aria-hidden="true">{'\u25A6'}</span>
                        Campaigns
                    </button>

                    <button
                        type="button"
                        className={`nav-item ${
                            page === 'compose' && !savedCampaign ? 'active' : ''
                        }`}
                        aria-current={
                            page === 'compose' && !savedCampaign ? 'page' : undefined
                        }
                        disabled={busy}
                        onClick={openComposer}
                    >
                        <span aria-hidden="true">+</span>
                        {hasUnsavedDraft ? 'Continue draft' : 'New campaign'}
                    </button>
                </nav>

                <div className="sidebar-note">
                    <span className="note-label">YOUR NEXT SEND</span>
                    <h3>Good emails start with a clear message.</h3>
                    <p>
                        Write your message, add recipients, then review before sending.
                    </p>
                </div>

                <div className="workspace-label">
          <span className="avatar" aria-hidden="true">
            W
          </span>

                    <div>
                        <strong>My workspace</strong>
                        <small>Email campaigns</small>
                    </div>
                </div>
            </aside>

            <div className="main-shell">
                <header className="topbar">
          <span>
            Workspace
            <span className="separator">/</span>
              {page === 'overview'
                  ? 'Campaigns'
                  : savedCampaign
                      ? `Campaign #${savedCampaign.id}`
                      : 'Compose'}
          </span>

                    <span className="environment-badge">Local workspace</span>
                </header>

                <main id="main-content" tabIndex={-1}>
                    {page === 'overview' ? (
                        <>
                            <div className="page-heading">
                                <div>
                                    <p className="eyebrow">CAMPAIGN WORKSPACE</p>
                                    <h1>Your campaigns.</h1>
                                    <p className="muted">
                                        Create messages, manage recipient imports and follow delivery.
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    className="button primary"
                                    disabled={busy}
                                    onClick={openComposer}
                                >
                                    <span aria-hidden="true">+</span>
                                    {hasUnsavedDraft ? 'Continue draft' : 'New campaign'}
                                </button>
                            </div>

                            {hasUnsavedDraft && (
                                <p className="feedback draft-reminder" role="status">
                                    You have an unsaved draft. Use Continue draft to resume it.
                                    Refreshing the page will discard unsaved text.
                                </p>
                            )}

                            {openingCampaign && (
                                <p className="dashboard-notice" role="status">
                                    Opening campaign...
                                </p>
                            )}

                            {openError && (
                                <div
                                    className="feedback error-feedback dashboard-notice"
                                    role="alert"
                                >
                                    {openError}
                                </div>
                            )}

                            <CampaignListPanel
                                opening={busy}
                                onOpen={(id) => void openCampaignById(id)}
                            />

                            <details className="panel campaign-id-panel">
                                <summary>Open a campaign by ID</summary>

                                <form
                                    className="editor"
                                    onSubmit={handleOpenCampaign}
                                    aria-busy={openingCampaign}
                                >
                                    <label htmlFor="existing-campaign-id">
                                        Campaign ID
                                    </label>

                                    <input
                                        id="existing-campaign-id"
                                        type="number"
                                        min="1"
                                        step="1"
                                        required
                                        value={campaignIdToOpen}
                                        onChange={(event) => {
                                            setCampaignIdToOpen(event.target.value)
                                            setOpenError('')
                                        }}
                                        placeholder="e.g. 6"
                                        disabled={busy}
                                        aria-describedby="campaign-id-help"
                                    />

                                    <p className="draft-note" id="campaign-id-help">
                                        Use this shortcut when you already know the campaign ID.
                                    </p>

                                    <div className="editor-actions">
                                        <button
                                            type="submit"
                                            className="button secondary"
                                            disabled={busy}
                                        >
                                            {openingCampaign ? 'Opening...' : 'Open campaign'}
                                        </button>
                                    </div>
                                </form>
                            </details>
                        </>
                    ) : (
                        <>
                            <div className="page-heading">
                                <div>
                                    <p className="eyebrow">
                                        {savedCampaign
                                            ? `CAMPAIGN #${savedCampaign.id}`
                                            : 'STEP 01 / COMPOSE'}
                                    </p>

                                    <h1>
                                        {savedCampaign
                                            ? savedCampaign.name
                                            : 'A message worth opening.'}
                                    </h1>

                                    <p className="muted">
                                        {savedCampaign
                                            ? 'Review your message, recipients and delivery progress.'
                                            : 'Start with the essentials. Preview your email as you write.'}
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    className="button secondary"
                                    disabled={busy}
                                    onClick={showOverview}
                                >
                                    Back to campaigns
                                </button>
                            </div>

                            <div className="composer-grid">
                                <section
                                    className="panel editor"
                                    aria-labelledby="details-title"
                                >
                                    <h2 id="details-title">Campaign details</h2>

                                    <p className="section-description">
                                        {savedCampaign
                                            ? 'Your saved campaign message is shown below.'
                                            : 'Save your message as a draft before adding recipients.'}
                                    </p>

                                    <form onSubmit={handleSave} aria-busy={isSaving}>
                                        <fieldset
                                            className="editor-fields"
                                            disabled={busy || savedCampaign !== null}
                                        >
                                            <label htmlFor="campaign-name">Campaign name</label>

                                            <input
                                                id="campaign-name"
                                                name="name"
                                                value={name}
                                                onChange={(event) => {
                                                    setName(event.target.value)
                                                    setSaveError('')
                                                }}
                                                maxLength={150}
                                                required
                                                placeholder="e.g. October placement updates"
                                            />

                                            <label htmlFor="email-subject">Email subject</label>

                                            <input
                                                id="email-subject"
                                                name="subject"
                                                value={subject}
                                                onChange={(event) => {
                                                    setSubject(event.target.value)
                                                    setSaveError('')
                                                }}
                                                maxLength={200}
                                                required
                                                placeholder="What should your recipients know?"
                                            />

                                            <label htmlFor="email-message">Message</label>

                                            <textarea
                                                id="email-message"
                                                name="message"
                                                value={message}
                                                onChange={(event) => {
                                                    setMessage(event.target.value)
                                                    setSaveError('')
                                                }}
                                                rows={11}
                                                maxLength={10000}
                                                required
                                                placeholder={'Hello,\n\nWrite your message here...'}
                                                aria-describedby="message-help"
                                            />

                                            <p className="draft-note" id="message-help">
                                                {message.length.toLocaleString()} / 10,000 characters.
                                                {!savedCampaign &&
                                                    ' Saving creates a draft; it does not send emails.'}
                                            </p>
                                        </fieldset>

                                        {saveError && (
                                            <div className="feedback error-feedback" role="alert">
                                                {saveError}
                                            </div>
                                        )}

                                        {savedCampaign && (
                                            <div className="feedback success-feedback" role="status">
                                                <strong>Campaign stored on server.</strong>

                                                <p>
                                                    {savedCampaign.name}
                                                    {' / '}ID: {savedCampaign.id}
                                                    {' / '}Current status: {savedCampaign.status}
                                                </p>

                                                <p>
                                                    {savedCampaign.status === 'DRAFT'
                                                        ? 'Add recipients, then review and start your campaign below.'
                                                        : 'View delivery progress below.'}
                                                </p>
                                            </div>
                                        )}

                                        <div className="editor-actions">
                                            {savedCampaign ? (
                                                <button
                                                    type="button"
                                                    className="button secondary"
                                                    disabled={busy}
                                                    onClick={startAnotherDraft}
                                                >
                                                    Write another campaign
                                                </button>
                                            ) : (
                                                <button
                                                    type="submit"
                                                    className="button primary"
                                                    disabled={busy}
                                                >
                                                    {isSaving ? 'Saving campaign...' : 'Save campaign'}
                                                </button>
                                            )}
                                        </div>

                                        {!savedCampaign && (
                                            <p className="draft-note">
                                                Unsaved text will be lost if you refresh the page.
                                            </p>
                                        )}
                                    </form>
                                </section>

                                <aside
                                    className="preview-column"
                                    aria-labelledby="preview-title"
                                >
                                    <div className="preview-heading">
                                        <h2 id="preview-title">Email preview</h2>
                                        <span className="pill">
                      {savedCampaign ? 'SAVED' : 'LIVE'}
                    </span>
                                    </div>

                                    <div className="email-preview">
                                        <div className="email-meta">
                      <span className="avatar" aria-hidden="true">
                        S
                      </span>

                                            <div>
                                                <strong>Your campaign</strong>
                                                <small>To: your recipients</small>
                                            </div>
                                        </div>

                                        <h3>{subject.trim() || 'Your subject appears here'}</h3>

                                        <div
                                            className={`email-body ${message ? '' : 'placeholder'}`}
                                        >
                                            {message ||
                                                'Your email message will appear here as you type.'}
                                        </div>
                                    </div>

                                    <div className="preview-caption">
                                        <strong>{name.trim() || 'Untitled campaign'}</strong>
                                        <p>
                                            Text preview. Final appearance can vary by email client.
                                        </p>
                                    </div>
                                </aside>
                            </div>
                        </>
                    )}

                    {savedCampaign && (
                        <div hidden={page !== 'compose'}>
                            {savedCampaign.status === 'DRAFT' && (
                                <RecipientImportPanel
                                    key={`import-${savedCampaign.id}`}
                                    campaignId={savedCampaign.id}
                                />
                            )}

                            <RecipientImportHistoryPanel
                                key={`history-${savedCampaign.id}`}
                                campaignId={savedCampaign.id}
                            />

                            <CampaignDeliveryPanel
                                key={`delivery-${savedCampaign.id}`}
                                campaignId={savedCampaign.id}
                                onStatusChange={handleCampaignStatusChange}
                            />
                        </div>
                    )}

                    <footer className="page-footer">
                        SendFlow
                        <span>Prepare carefully. Send confidently.</span>
                    </footer>
                </main>
            </div>
        </div>
    )
}

export default App