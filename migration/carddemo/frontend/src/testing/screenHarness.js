/**
 * Test harness for the migrated screens.
 *
 * Every screen talks to the Spring Boot backend with `fetch`, so the tests stub
 * `fetch` itself rather than the api/ modules: that keeps the request the screen
 * actually sends (the paging cursors, the AID key, the selection flags) inside
 * the assertion surface, which is what the BMS/COBOL paging and selection rules
 * are about.
 *
 * Screens are rendered inside the real router and the real AuthProvider so the
 * shared Layout renders as it does in the app, and `location` tracks the route
 * the screen navigates to (the migrated equivalent of an XCTL).
 */
import React from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';

/** The COMMAREA the shell restores on load: an administrator, as CU00/CTLI need. */
export const SIGNED_ON_ADMIN = { userId: 'ADMIN001', userType: 'A' };

function toResponse(result) {
  const hasEnvelope = result !== null
    && typeof result === 'object'
    && Object.prototype.hasOwnProperty.call(result, 'status');
  const status = hasEnvelope ? result.status : 200;
  const body = hasEnvelope ? result.body : result;
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined || body === null) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return body;
    },
  };
}

/**
 * Installs a `fetch` stub and returns the recorder/registrar.
 *
 * `backend.get('/api/admin/users', handler)` matches on the path only, so the
 * query string stays available for assertions; a RegExp matcher is matched
 * against the whole URL. A handler is either a value (a body, or a
 * `{status, body}` envelope), an array consumed one entry per call (the last
 * entry repeats), or a function of `(call, callIndex)`.
 */
export function stubBackend() {
  const routes = [];
  const calls = [];

  global.fetch = jest.fn(async (input, init = {}) => {
    const url = String(input);
    const method = (init.method || 'GET').toUpperCase();
    const call = { url, method, body: init.body ? JSON.parse(init.body) : null };
    calls.push(call);
    const route = routes.find((r) => r.method === method && r.matches(url));
    if (!route) {
      throw new Error(`No stub registered for ${method} ${url}`);
    }
    const index = route.count;
    route.count += 1;
    let result = route.handler;
    if (Array.isArray(result)) {
      result = result[Math.min(index, result.length - 1)];
    } else if (typeof result === 'function') {
      result = result(call, index);
    }
    return toResponse(result);
  });

  const backend = {
    calls,
    on(method, matcher, handler) {
      const matches = typeof matcher === 'string'
        ? (url) => url.split('?')[0] === matcher
        : (url) => matcher.test(url);
      // Newest first, so a test can override a default registered earlier.
      routes.unshift({ method, matches, handler, count: 0 });
      return backend;
    },
    get: (matcher, handler) => backend.on('GET', matcher, handler),
    post: (matcher, handler) => backend.on('POST', matcher, handler),
    put: (matcher, handler) => backend.on('PUT', matcher, handler),
    delete: (matcher, handler) => backend.on('DELETE', matcher, handler),
    /** Every recorded call to a method+path, in order. */
    callsTo(method, path) {
      return calls.filter((c) => c.method === method && c.url.split('?')[0] === path);
    },
  };

  backend.get('/api/auth/session', SIGNED_ON_ADMIN);
  return backend;
}

/** Renders a screen in the router + auth shell; `location` follows navigation. */
export function renderScreen(ui, { route = '/' } = {}) {
  const location = { pathname: route, search: '' };

  function LocationProbe() {
    const current = useLocation();
    location.pathname = current.pathname;
    location.search = current.search;
    return null;
  }

  const utils = render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <LocationProbe />
        {ui}
      </AuthProvider>
    </MemoryRouter>,
  );
  return { ...utils, location };
}

/** The current path plus query string, i.e. the XCTL target of a selection. */
export function currentUrl(location) {
  return `${location.pathname}${location.search}`;
}
