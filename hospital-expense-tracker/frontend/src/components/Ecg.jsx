export default function Ecg({ className = '' }) {
  return (
    <svg className={'ecg ' + className} viewBox="0 0 600 120" preserveAspectRatio="none" aria-hidden="true">
      <path pathLength="1000" d="M0 60 H120 L140 60 L155 20 L175 100 L195 40 L210 60 H330 L350 60 L365 25 L385 95 L405 45 L420 60 H600" />
    </svg>
  );
}
