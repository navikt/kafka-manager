import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './app';
import * as dayjs from 'dayjs';
import 'dayjs/locale/nb';

dayjs.locale('nb');

if (import.meta.env.DEV) {
	// Siden registrering av MSW service-workers er en asynkron operasjon
	// kan det oppstå en race-condition der applikasjonen klarer å sende
	// avgårde requester før MSW er klar til å håndtere de.
	//
	// Ved å conditionally kjøre ReactDOM.render først etter at MSW
	// er klar kan vi unngå denne race-conditionen.

	const { worker } = await import('./mock');

	try {
		await worker.start({
			onUnhandledRequest: 'bypass',
			serviceWorker: { url: import.meta.env.BASE_URL + 'mockServiceWorker.js' }
		});

		mountReactApp();
	} catch (e) {
		// tslint:disable-next-line:no-console
		console.error('Unable to setup mocked API endpoints', e);
	}
} else {
	mountReactApp();
}

function mountReactApp() {
	const domNode = document.getElementById('root');
	// @ts-ignore
	const reactRoot = createRoot(domNode);
	reactRoot.render(<App />);
}
