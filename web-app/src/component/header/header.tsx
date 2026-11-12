import { Detail } from '@navikt/ds-react'
import './header.css'

export function Header() {
	const imageVersion = import.meta.env.VITE_APP_VERSION

	return (
		<header className="header">
			<h1 className="header__title">Kafka Manager</h1>
			{imageVersion && (
				<Detail as="span" className="header__version">
					Versjon {imageVersion}
				</Detail>
			)}
		</header>
	)
}
