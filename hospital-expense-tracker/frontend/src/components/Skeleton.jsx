export default function Skeleton({ rows = 5 }) {
  return (
    <div aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => <div className="skel" key={i} />)}
    </div>
  );
}
