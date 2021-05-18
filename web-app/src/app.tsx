import React from 'react';
import { ToastContainer } from 'react-toastify';
import './app.less';
import 'react-toastify/dist/ReactToastify.css';
import { KafkaAdmin } from './view/kafka-admin/kafka-admin';

function App() {
	return (
		<div className="app kafka-manager">
			<main>
				<KafkaAdmin/>
			</main>
			<ToastContainer/>
		</div>
	);
}

export default App;
