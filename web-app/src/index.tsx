import React from 'react';
import ReactDOM from 'react-dom';
import App from './app';
import * as dayjs from 'dayjs';
import 'dayjs/locale/nb';

dayjs.locale('nb');

if (process.env.REACT_APP_DEV) {
	require('./mock');
}

ReactDOM.render(<App />, document.getElementById('root'));
