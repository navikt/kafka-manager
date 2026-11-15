import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastContainer } from 'react-toastify'
import './app.css'
import 'react-toastify/dist/ReactToastify.css'
import { KafkaAdmin } from './view/kafka-admin/KafkaAdmin'
import { Header } from './component/header/header'

const queryClient = new QueryClient()

function App() {
	return (
		<QueryClientProvider client={queryClient}>
			<div className="app kafka-manager">
				<Header />
				<main>
					<KafkaAdmin />
				</main>
				<ToastContainer />
			</div>
		</QueryClientProvider>
	)
}

export default App
