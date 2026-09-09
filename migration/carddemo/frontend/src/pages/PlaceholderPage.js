import React from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../components/Layout';

/**
 * The route a registry entry gets until its stream session migrates the screen,
 * so both menus are navigable end to end from the first day. It states which
 * COBOL program and which stream own the screen; the stream session replaces it
 * by setting that entry's `element` in the route registry.
 */
const PF_KEYS = 'ENTER=Continue  F3=Exit';

const styles = {
  body: { lineHeight: 1.8 },
  keys: { marginTop: '24px' },
  button: {
    font: 'inherit',
    padding: '4px 12px',
    border: '1px solid #2c5d34',
    background: '#e8f5e8',
    cursor: 'pointer',
  },
};

export default function PlaceholderPage({ screen }) {
  const navigate = useNavigate();

  return (
    <Layout
      tranId={screen.tranId}
      progName={screen.program}
      title={screen.title}
      pfKeys={PF_KEYS}
    >
      <div style={styles.body}>
        <div>{`${screen.program} has not been migrated yet.`}</div>
        <div>{`Migration stream: ${screen.stream}`}</div>
        <div>{`Route: ${screen.path}`}</div>
      </div>
      <div style={styles.keys}>
        <button type="button" style={styles.button} onClick={() => navigate(-1)}>
          PF3 — Back
        </button>
      </div>
    </Layout>
  );
}
