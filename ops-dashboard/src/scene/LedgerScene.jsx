import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Html, MeshReflectorMaterial, OrbitControls, RoundedBox } from '@react-three/drei';
import * as THREE from 'three';
import { deskName, money } from '../format.js';

const METALS = ['#e2c17a', '#d7dee6', '#c5d0d8', '#f0d59a', '#d2bfa4'];
const LOOK_AT = new THREE.Vector3(0, 0.72, 0);

function barHeight(balance) {
  const value = Math.max(Number(balance) || 0, 0);
  const t = Math.log10(value + 10);
  const normalized = (t - 1) / 4;
  return THREE.MathUtils.clamp(0.42 + normalized * 2.55, 0.42, 3.05);
}

function placement(count, aspect) {
  const tight = aspect < 0.95;
  const spacing = tight ? 1.05 : 1.46;
  const cap = tight ? 3.2 : 4.7;
  const span = count <= 1 ? 0 : Math.min(cap, Math.max(tight ? 1.7 : 2.4, (count - 1) * spacing));
  return { span, tight };
}

function slotPosition(index, count, span) {
  if (count <= 1) return [0, 0, 0.12];
  const x = -span / 2 + (span * index) / (count - 1);
  const z = Math.abs(index - (count - 1) / 2) * 0.14;
  return [x, 0, z];
}

function Frame({ span, aspect }) {
  const { camera, size, controls } = useThree();
  useLayoutEffect(() => {
    const tight = aspect < 0.95;
    const dist = tight ? 8.6 : 7.4;
    const needed = span + (tight ? 1.2 : 1.5);
    const safeAspect = Math.max(aspect, 0.42);
    const tanHalf = needed / (2 * dist * safeAspect);
    const fov = THREE.MathUtils.clamp(THREE.MathUtils.radToDeg(2 * Math.atan(tanHalf)), 30, 50);
    camera.fov = fov;
    camera.position.set(0, tight ? 4.05 : 3.45, dist);
    camera.near = 0.08;
    camera.far = 40;
    camera.lookAt(LOOK_AT);
    camera.updateProjectionMatrix();
    if (controls) {
      controls.target.copy(LOOK_AT);
      controls.update();
    }
  }, [camera, controls, span, aspect, size.width, size.height]);
  return null;
}

function Bullion({ account, risk, selected, position, reduceMotion, onSelect }) {
  const group = useRef();
  const bar = useRef();
  const [hovered, setHovered] = useState(false);
  const target = barHeight(account.balance);
  const current = useRef(target);
  const flagged = risk >= 0.7;
  const metal = flagged ? '#c4654e' : METALS[(account.accountId || 1) % METALS.length];

  useFrame((_, delta) => {
    const next = reduceMotion ? target : THREE.MathUtils.damp(current.current, target, 4.5, delta);
    current.current = next;
    if (bar.current) {
      bar.current.scale.y = next;
      bar.current.position.y = next / 2;
    }
    if (group.current && !reduceMotion) {
      const lift = hovered || selected ? 0.06 : 0;
      group.current.position.y = THREE.MathUtils.damp(group.current.position.y, lift, 6, delta);
    }
  });

  return (
    <group ref={group} position={position}>
      <mesh position={[0, 0.045, 0]}>
        <cylinderGeometry args={[0.5, 0.58, 0.08, 32]} />
        <meshStandardMaterial color="#17191f" metalness={0.84} roughness={0.38} />
      </mesh>
      {(flagged || selected) && (
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.09, 0]}>
          <ringGeometry args={[0.46, 0.54, 40]} />
          <meshStandardMaterial
            color={flagged ? '#e07a68' : '#d7b56d'}
            emissive={flagged ? '#e07a68' : '#d7b56d'}
            emissiveIntensity={flagged ? 0.7 : 0.4}
            roughness={0.35}
            metalness={0.4}
          />
        </mesh>
      )}
      <RoundedBox
        ref={bar}
        args={[0.66, 1, 0.66]}
        radius={0.045}
        smoothness={3}
        position={[0, target / 2, 0]}
        scale={[1, target, 1]}
        castShadow
        onClick={(event) => {
          event.stopPropagation();
          onSelect(account.accountId);
        }}
        onPointerOver={(event) => {
          event.stopPropagation();
          setHovered(true);
          document.body.style.cursor = 'pointer';
        }}
        onPointerOut={() => {
          setHovered(false);
          document.body.style.cursor = '';
        }}
      >
        <meshStandardMaterial
          color={metal}
          metalness={0.94}
          roughness={selected || hovered ? 0.18 : 0.32}
          emissive={selected ? '#d7b56d' : flagged ? '#e07a68' : metal}
          emissiveIntensity={selected ? 0.24 : flagged ? 0.4 : 0.1}
        />
      </RoundedBox>
      <Html position={[0, THREE.MathUtils.clamp(target * 0.58 + 0.2, 0.72, 1.78), 0]} center distanceFactor={6.5} zIndexRange={[4, 0]} wrapperClass="bullion-anchor" style={{ pointerEvents: 'none' }}>
        <div className={`bullion-label ${selected ? 'is-selected' : ''}`}>
          <strong>{deskName(account.accountHolder)}</strong>
          <span>{money(account.balance)}</span>
        </div>
      </Html>
    </group>
  );
}

function Spark({ from, to, color, onDone }) {
  const mesh = useRef();
  const progress = useRef(0);
  const finished = useRef(false);
  const start = useRef(new THREE.Vector3(from[0], from[1], from[2]));
  const end = useRef(new THREE.Vector3(to[0], to[1], to[2]));

  useFrame((_, delta) => {
    progress.current = Math.min(1, progress.current + delta / 0.95);
    const eased = 1 - (1 - progress.current) ** 3;
    if (mesh.current) {
      mesh.current.position.lerpVectors(start.current, end.current, eased);
      mesh.current.position.y += Math.sin(progress.current * Math.PI) * 0.85;
      const fade = progress.current > 0.75 ? 1 - (progress.current - 0.75) / 0.25 : 1;
      mesh.current.scale.setScalar(0.7 + fade * 0.5);
    }
    if (progress.current >= 1 && !finished.current) {
      finished.current = true;
      onDone();
    }
  });

  return (
    <mesh ref={mesh} position={start}>
      <sphereGeometry args={[0.055, 18, 18]} />
      <meshStandardMaterial color={color} emissive={color} emissiveIntensity={1.8} />
    </mesh>
  );
}

function Seal({ reduceMotion }) {
  const mesh = useRef();
  useFrame((_, delta) => {
    if (mesh.current && !reduceMotion) mesh.current.rotation.z -= delta * 0.12;
  });
  return (
    <mesh ref={mesh} rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.025, 0.2]}>
      <torusGeometry args={[1.28, 0.011, 12, 90]} />
      <meshStandardMaterial color="#d7b56d" metalness={1} roughness={0.2} />
    </mesh>
  );
}

function Dust({ reduceMotion }) {
  const points = useRef();
  const positions = useMemo(() => {
    const values = new Float32Array(160 * 3);
    for (let index = 0; index < 160; index += 1) {
      values[index * 3] = (Math.random() - 0.5) * 12;
      values[index * 3 + 1] = Math.random() * 3.4 + 0.2;
      values[index * 3 + 2] = (Math.random() - 0.5) * 8;
    }
    return values;
  }, []);

  useFrame((_, delta) => {
    if (points.current && !reduceMotion) points.current.rotation.y += delta * 0.012;
  });

  return (
    <points ref={points}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
      </bufferGeometry>
      <pointsMaterial color="#e6d3a8" size={0.018} transparent opacity={0.38} depthWrite={false} />
    </points>
  );
}

function Floor() {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]}>
      <planeGeometry args={[22, 22]} />
      <MeshReflectorMaterial
        blur={[260, 80]}
        resolution={512}
        mixBlur={0.8}
        mixStrength={0.45}
        roughness={0.95}
        depthScale={1.05}
        minDepthThreshold={0.35}
        maxDepthThreshold={1.3}
        color="#12141a"
        metalness={0.62}
      />
    </mesh>
  );
}

function flightColor(flight) {
  if (flight.status === 'FLAGGED') return '#e07a68';
  if (flight.counterpartyAccountId) return '#8eb8b6';
  return flight.transactionType === 'CREDIT' ? '#8fbfa3' : '#d7b56d';
}

function endpoints(flight, layout) {
  const own = layout.get(Number(flight.accountId));
  if (flight.counterpartyAccountId && flight.transactionType === 'DEBIT') {
    const other = layout.get(Number(flight.counterpartyAccountId));
    if (own && other) {
      return { from: [own[0], 1.15, own[2]], to: [other[0], 1.35, other[2]] };
    }
  }
  const dest = own || [0, 0, 0];
  return { from: [0, 1.7, 0.2], to: [dest[0], 1.25, dest[2]] };
}

function SceneContents({ accounts, riskByAccount, flights, selectedId, reduceMotion, onSelect, onFlightDone }) {
  const size = useThree((state) => state.size);
  const aspect = size.width / Math.max(size.height, 1);
  const signature = accounts.map((account) => account.accountId).join(',');
  const { span, map: layout } = useMemo(() => {
    const { span: nextSpan } = placement(accounts.length, aspect);
    const map = new Map();
    accounts.forEach((account, index) => {
      map.set(Number(account.accountId), slotPosition(index, accounts.length, nextSpan));
    });
    return { span: nextSpan, map };
  }, [signature, accounts.length, aspect]);

  return (
    <>
      <Frame span={span} aspect={aspect} />
      <hemisphereLight args={['#f4ecdf', '#1c1e26', 0.72]} />
      <ambientLight intensity={0.34} />
      <spotLight position={[3.2, 8.2, 4.2]} angle={0.72} penumbra={0.85} intensity={38} color="#f6ead2" />
      <pointLight position={[-3.6, 2.8, 2.2]} intensity={8} color="#9eb8b6" />
      <directionalLight position={[5.5, 3.2, 1.2]} intensity={0.85} color="#f0d7a4" />
      <Seal reduceMotion={reduceMotion} />
      <Dust reduceMotion={reduceMotion} />
      <Floor />
      {accounts.map((account) => (
        <Bullion
          key={account.accountId}
          account={account}
          risk={riskByAccount[account.accountId] || 0}
          selected={Number(selectedId) === Number(account.accountId)}
          position={layout.get(Number(account.accountId)) || [0, 0, 0]}
          reduceMotion={reduceMotion}
          onSelect={onSelect}
        />
      ))}
      {!reduceMotion && flights.map((flight) => {
        const path = endpoints(flight, layout);
        return (
          <Spark
            key={flight.transactionId}
            from={path.from}
            to={path.to}
            color={flightColor(flight)}
            onDone={() => onFlightDone(flight.transactionId)}
          />
        );
      })}
      <OrbitControls
        makeDefault
        enablePan={false}
        enableDamping
        dampingFactor={0.08}
        minPolarAngle={0.7}
        maxPolarAngle={1.25}
        minDistance={4.2}
        maxDistance={12}
        target={[0, 0.72, 0]}
      />
    </>
  );
}

export default function LedgerScene(props) {
  return (
    <Canvas
      camera={{ position: [0, 3.45, 7.4], fov: 33 }}
      dpr={[1, 1.6]}
      gl={{ antialias: true, alpha: false, powerPreference: 'high-performance' }}
      onCreated={({ gl }) => {
        gl.setClearColor('#0b0c0f');
        gl.toneMapping = THREE.ACESFilmicToneMapping;
        gl.toneMappingExposure = 1.12;
      }}
    >
      <fog attach="fog" args={['#0b0c0f', 11, 20]} />
      <SceneContents {...props} />
    </Canvas>
  );
}
