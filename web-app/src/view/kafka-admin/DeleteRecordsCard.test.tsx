import { renderToStaticMarkup } from 'react-dom/server'
import { DeleteRecordsCard } from './DeleteRecordsCard'

test('disables record deletion for unsupported topics', () => {
	const html = renderToStaticMarkup(
		<DeleteRecordsCard selectedTopic="on-prem-topic" supportsRecordDeletion={false} />
	)

	expect(html).toContain('Record deletion is only supported for Aiven topics.')
	expect(html).toMatch(/<button[^>]*disabled/)
})
