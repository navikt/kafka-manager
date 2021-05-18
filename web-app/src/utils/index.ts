const DEV_DOMAINS = ['dev', 'localhost', 'app-q1', 'app-q0'];

// tslint:disable-next-line:no-empty
export const NO_OP = () => {};

export const erITestMiljo = (): boolean => {
	return window.location.hostname.split('.').findIndex(domain => DEV_DOMAINS.includes(domain)) >= 0;
};

export const isJson = (maybeJson: string): boolean => {
	try {
		JSON.parse(maybeJson);
		return true;
	} catch (e) {
		return false;
	}
};
