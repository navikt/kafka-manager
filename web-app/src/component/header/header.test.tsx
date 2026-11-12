import { renderToStaticMarkup } from 'react-dom/server'
import { Header } from './header'

afterEach(() => vi.unstubAllEnvs())

test('displays the image build tag', () => {
	vi.stubEnv('VITE_APP_VERSION', '2026.11.12-16.12-abcdef0')

	const html = renderToStaticMarkup(<Header />)

	expect(html).toContain('Versjon 2026.11.12-16.12-abcdef0')
})
